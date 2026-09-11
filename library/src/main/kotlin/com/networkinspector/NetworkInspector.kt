package com.networkinspector

import android.content.Context
import android.content.Intent
import android.util.Log
import com.networkinspector.core.NetworkInspectorConfig
import com.networkinspector.core.NetworkRequest
import com.networkinspector.core.RequestStats
import com.networkinspector.core.RequestStatus
import com.networkinspector.notification.InspectorNotificationManager
import com.networkinspector.ui.RequestListActivity
import com.networkinspector.util.BodyFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * NetworkInspector - A lightweight network request inspector for Android.
 * 
 * Similar to Chucker but simpler and without OkHttp dependency.
 * Can be integrated with any HTTP client.
 * 
 * ## Usage:
 * 
 * ### 1. Initialize in Application.onCreate():
 * ```kotlin
 * NetworkInspector.init(this, NetworkInspectorConfig.DEBUG)
 * // or simply:
 * NetworkInspector.init(this)
 * ```
 * 
 * ### 2. Track requests in your network layer:
 * ```kotlin
 * // When request starts
 * val requestId = NetworkInspector.onRequestStart(url, "POST", body = jsonBody)
 * 
 * // When request succeeds
 * NetworkInspector.onRequestSuccess(requestId, 200, responseBody)
 * 
 * // When request fails
 * NetworkInspector.onRequestFailed(requestId, 500, exception)
 * ```
 * 
 * ### 3. Open the UI:
 * ```kotlin
 * NetworkInspector.launch(context)
 * ```
 */
object NetworkInspector {
    
    private const val TAG = "NetworkInspector"
    
    private var appContext: Context? = null
    private var config: NetworkInspectorConfig = NetworkInspectorConfig.RELEASE
    private var notificationManager: InspectorNotificationManager? = null
    
    // Request storage
    private val requests = CopyOnWriteArrayList<NetworkRequest>()
    private val activeRequests = ConcurrentHashMap<String, NetworkRequest>()
    private val requestIdGenerator = AtomicLong(0)
    
    // Stats
    private var totalRequests = 0
    private var successfulRequests = 0
    private var failedRequests = 0
    private var activeRequestCount = 0
    
    // Listeners
    private val listeners = CopyOnWriteArrayList<RequestListener>()
    
    // Background executor for heavy formatting work
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "NetworkInspector-Worker").apply { isDaemon = true }
    }
    
    // ==================== Initialization ====================
    
    /**
     * Initialize NetworkInspector with default debug configuration.
     * Call this in Application.onCreate()
     */
    @JvmStatic
    fun init(context: Context) {
        init(context, NetworkInspectorConfig.DEBUG)
    }
    
    /**
     * Initialize NetworkInspector with custom configuration.
     * Call this in Application.onCreate()
     */
    @JvmStatic
    fun init(context: Context, config: NetworkInspectorConfig) {
        appContext = context.applicationContext
        this.config = config
        
        if (config.enabled && config.showNotification) {
            notificationManager = InspectorNotificationManager(context.applicationContext, config)
        }
        
        if (config.logToLogcat) {
            Log.d(TAG, "NetworkInspector initialized (enabled: ${config.enabled})")
        }
    }
    
    /**
     * Check if inspector is enabled
     */
    @JvmStatic
    fun isEnabled(): Boolean = config.enabled
    
    // ==================== Request Tracking ====================
    
    /**
     * Record start of a network request.
     * 
     * @param url The full URL of the request
     * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param params Query parameters (optional)
     * @param headers Request headers (optional)
     * @param body Request body (optional)
     * @param tag Custom tag for categorization (optional)
     * @return Request ID to use for completion tracking
     */
    @JvmStatic
    @JvmOverloads
    fun onRequestStart(
        url: String,
        method: String = "GET",
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        body: Any? = null,
        tag: String? = null
    ): String {
        return try {
            if (!config.enabled) return ""
            if (!config.shouldTrack(url)) return ""
            
            val requestId = "req_${requestIdGenerator.incrementAndGet()}_${System.currentTimeMillis()}"
            
            val bodyString = try { BodyFormatter.format(body, config.maxBodySize) } catch (e: Throwable) { null }
            
            val request = NetworkRequest(
                id = requestId,
                url = url,
                method = method.uppercase(),
                params = params,
                headers = headers,
                requestBody = bodyString,
                startTime = System.currentTimeMillis(),
                status = RequestStatus.IN_PROGRESS,
                tag = tag
            )
            
            activeRequests[requestId] = request
            activeRequestCount++
            totalRequests++
            
            if (config.logToLogcat) {
                Log.d(TAG, "📤 [${request.method}] ${request.shortName}")
            }
            
            try { notificationManager?.updateNotification(getStats()) } catch (e: Throwable) { }
            try { notifyListeners() } catch (e: Throwable) { }
            
            requestId
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onRequestStart", e)
            ""
        }
    }
    
    /**
     * Record successful completion of a network request.
     * 
     * @param requestId The ID returned from onRequestStart
     * @param responseCode HTTP response code
     * @param response Response body (String, Object, or null)
     * @param headers Response headers (optional)
     */
    @JvmStatic
    @JvmOverloads
    fun onRequestSuccess(
        requestId: String,
        responseCode: Int = 200,
        response: Any? = null,
        headers: Map<String, String>? = null
    ) {
        try {
            if (!config.enabled || requestId.isEmpty()) return
            
            val request = activeRequests.remove(requestId) ?: return
            activeRequestCount--
            successfulRequests++
            
            val endTime = System.currentTimeMillis()
            
            // Run heavy formatting work on background thread to avoid ANR
            executor.execute {
                try {
                    val responseBody = formatResponse(response)
                    
                    val completedRequest = request.copy(
                        status = RequestStatus.SUCCESS,
                        responseCode = responseCode,
                        responseBody = responseBody,
                        responseHeaders = headers,
                        endTime = endTime,
                        duration = endTime - request.startTime
                    )
                    
                    addRequest(completedRequest)
                    
                    if (config.logToLogcat) {
                        Log.d(TAG, "✅ [${completedRequest.method}] ${completedRequest.shortName} " +
                                "| $responseCode | ${completedRequest.formattedDuration}")
                    }
                    
                    try { notificationManager?.updateNotification(getStats()) } catch (e: Throwable) { }
                    try { notifyListeners() } catch (e: Throwable) { }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error processing response", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onRequestSuccess", e)
        }
    }
    
    /**
     * Record failed network request.
     * 
     * @param requestId The ID returned from onRequestStart
     * @param responseCode HTTP response code (0 if connection failed)
     * @param error Error object (Throwable, String, or any object)
     */
    @JvmStatic
    @JvmOverloads
    fun onRequestFailed(
        requestId: String,
        responseCode: Int = 0,
        error: Any? = null
    ) {
        try {
            if (!config.enabled || requestId.isEmpty()) return
            
            val request = activeRequests.remove(requestId) ?: return
            activeRequestCount--
            failedRequests++
            
            val endTime = System.currentTimeMillis()
            
            // Run on background thread to avoid ANR
            executor.execute {
                try {
                    val (errorMessage, stackTrace) = try {
                        when (error) {
                            is Throwable -> Pair(
                                error.message ?: error::class.java.simpleName,
                                error.stackTraceToString()
                            )
                            else -> Pair(error?.toString() ?: "Unknown error", null)
                        }
                    } catch (e: Throwable) {
                        Pair("Error", null)
                    }
                    
                    val completedRequest = request.copy(
                        status = RequestStatus.FAILED,
                        responseCode = if (responseCode != 0) responseCode else null,
                        errorMessage = errorMessage,
                        errorStackTrace = stackTrace,
                        endTime = endTime,
                        duration = endTime - request.startTime
                    )
                    
                    addRequest(completedRequest)
                    
                    if (config.logToLogcat) {
                        Log.e(TAG, "❌ [${completedRequest.method}] ${completedRequest.shortName} " +
                                "| ${completedRequest.formattedDuration} | $errorMessage")
                    }
                    
                    try { notificationManager?.updateNotification(getStats()) } catch (e: Throwable) { }
                    try { notifyListeners() } catch (e: Throwable) { }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error processing failure", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onRequestFailed", e)
        }
    }
    
    /**
     * Cancel a request (e.g., user cancelled or timeout)
     */
    @JvmStatic
    fun onRequestCancelled(requestId: String) {
        if (!config.enabled || requestId.isEmpty()) return
        
        val request = activeRequests.remove(requestId) ?: return
        activeRequestCount--
        
        val endTime = System.currentTimeMillis()
        val completedRequest = request.copy(
            status = RequestStatus.CANCELLED,
            endTime = endTime,
            duration = endTime - request.startTime,
            errorMessage = "Request cancelled"
        )
        
        addRequest(completedRequest)
        
        if (config.logToLogcat) {
            Log.w(TAG, "⚠️ [${completedRequest.method}] ${completedRequest.shortName} | Cancelled")
        }
        
        notificationManager?.updateNotification(getStats())
        notifyListeners()
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Get all recorded requests (newest first)
     */
    @JvmStatic
    fun getRequests(): List<NetworkRequest> = requests.toList()
    
    /**
     * Get requests filtered by status
     */
    @JvmStatic
    fun getRequests(status: RequestStatus): List<NetworkRequest> = 
        requests.filter { it.status == status }
    
    /**
     * Get a specific request by ID
     */
    @JvmStatic
    fun getRequest(id: String): NetworkRequest? = 
        requests.find { it.id == id } ?: activeRequests[id]
    
    /**
     * Search requests by URL or method
     */
    @JvmStatic
    fun searchRequests(query: String): List<NetworkRequest> {
        val lowerQuery = query.lowercase()
        return requests.filter { request ->
            request.url.lowercase().contains(lowerQuery) ||
            request.method.lowercase().contains(lowerQuery) ||
            request.tag?.lowercase()?.contains(lowerQuery) == true
        }
    }
    
    /**
     * Get current statistics
     */
    @JvmStatic
    fun getStats(): RequestStats = RequestStats(
        total = totalRequests,
        active = activeRequestCount,
        successful = successfulRequests,
        failed = failedRequests
    )
    
    /**
     * Clear all recorded requests
     */
    @JvmStatic
    fun clearAll() {
        requests.clear()
        totalRequests = 0
        successfulRequests = 0
        failedRequests = 0
        
        notificationManager?.dismiss()
        notifyListeners()
    }
    
    // ==================== UI Methods ====================
    
    /**
     * Launch the inspector UI
     */
    @JvmStatic
    fun launch(context: Context) {
        if (!config.enabled) return
        
        val intent = Intent(context, RequestListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Launch the analytics inspector UI
     */
    @JvmStatic
    fun launchAnalytics(context: Context) {
        if (!config.enabled) return
        
        val intent = Intent(context, com.networkinspector.ui.AnalyticsListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Get intent to launch the inspector UI
     */
    @JvmStatic
    fun getLaunchIntent(context: Context): Intent {
        return Intent(context, RequestListActivity::class.java)
    }
    
    /**
     * Get intent to launch the analytics inspector UI
     */
    @JvmStatic
    fun getAnalyticsLaunchIntent(context: Context): Intent {
        return Intent(context, com.networkinspector.ui.AnalyticsListActivity::class.java)
    }
    
    // ==================== Listener Methods ====================
    
    /**
     * Add a listener for request updates
     */
    @JvmStatic
    fun addListener(listener: RequestListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a listener
     */
    @JvmStatic
    fun removeListener(listener: RequestListener) {
        listeners.remove(listener)
    }
    
    // ==================== Internal Methods ====================
    
    private fun addRequest(request: NetworkRequest) {
        requests.add(0, request)
        
        // Trim if over limit
        while (requests.size > config.maxRequests) {
            requests.removeAt(requests.size - 1)
        }
    }
    
    private fun formatResponse(response: Any?): String? {
        return BodyFormatter.format(response, config.maxBodySize)
    }
    
    private fun notifyListeners() {
        val currentRequests = getRequests()
        val currentStats = getStats()
        listeners.forEach { 
            try {
                it.onRequestsUpdated(currentRequests, currentStats)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Listener interface for request updates
     */
    interface RequestListener {
        fun onRequestsUpdated(requests: List<NetworkRequest>, stats: RequestStats)
    }
}




