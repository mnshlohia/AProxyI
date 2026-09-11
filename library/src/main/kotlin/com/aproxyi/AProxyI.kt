package com.aproxyi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aproxyi.core.AProxyIConfig
import com.aproxyi.core.NetworkRequest
import com.aproxyi.core.RequestStats
import com.aproxyi.core.RequestStatus
import com.aproxyi.internal.notification.AProxyINotificationManager
import com.aproxyi.internal.ui.RequestListActivity
import com.aproxyi.internal.util.BodyFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * AProxyI - A lightweight network request inspector for Android.
 * 
 * Similar to Chucker but simpler and without OkHttp dependency.
 * Can be integrated with any HTTP client.
 * 
 * ## Usage:
 * 
 * ### 1. Initialize in Application.onCreate():
 * ```kotlin
 * AProxyI.init(this, AProxyIConfig.DEBUG)
 * // or simply:
 * AProxyI.init(this)
 * ```
 * 
 * ### 2. Track requests in your network layer:
 * ```kotlin
 * // When request starts
 * val requestId = AProxyI.onRequestStart(url, "POST", body = jsonBody)
 * 
 * // When request succeeds
 * AProxyI.onRequestSuccess(requestId, 200, responseBody)
 * 
 * // When request fails
 * AProxyI.onRequestFailed(requestId, 500, exception)
 * ```
 * 
 * ### 3. Open the UI:
 * ```kotlin
 * AProxyI.launch(context)
 * ```
 */
object AProxyI {
    
    private const val TAG = "AProxyI"
    
    private var appContext: Context? = null
    private var initialized = false
    private var config: AProxyIConfig = AProxyIConfig.RELEASE
    // Holds the APPLICATION context (init() stores context.applicationContext),
    // which outlives this object anyway, so this is not the activity leak lint
    // is warning about.
    @SuppressLint("StaticFieldLeak")
    private var notificationManager: AProxyINotificationManager? = null
    
    // Request storage
    // Guarded by synchronized(requests). A CopyOnWriteArrayList copied the whole
    // backing array on the add(0, ...) and again on every trim removal -- two
    // full copies of up to maxRequests entries per recorded request. Reads are
    // snapshot copies taken under the same lock.
    private val requests = ArrayDeque<NetworkRequest>()
    private val activeRequests = ConcurrentHashMap<String, NetworkRequest>()
    private val requestIdGenerator = AtomicLong(0)
    
    // Stats. Mutated from OkHttp dispatcher threads, the worker thread and
    // caller threads, so plain Ints would lose updates and read stale.
    private val totalRequests = AtomicInteger(0)
    private val successfulRequests = AtomicInteger(0)
    private val failedRequests = AtomicInteger(0)
    private val activeRequestCount = AtomicInteger(0)

    // Throttles the stale in-flight sweep, which runs on the request path.
    private val lastSweepAt = AtomicLong(0)
    
    // Listeners
    private val listeners = CopyOnWriteArrayList<RequestListener>()
    
    // Background executor for heavy formatting work
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AProxyI-Worker").apply { isDaemon = true }
    }
    
    // ==================== Initialization ====================
    
    /**
     * Initialize AProxyI with default debug configuration.
     * Call this in Application.onCreate()
     */
    @JvmStatic
    fun init(context: Context) {
        init(context, AProxyIConfig.DEBUG)
    }
    
    /**
     * Initialize AProxyI with custom configuration.
     * Call this in Application.onCreate()
     */
    @JvmStatic
    fun init(context: Context, config: AProxyIConfig) {
        synchronized(this) {
            if (initialized) {
                Log.w(TAG, "init() called more than once; ignoring the later call")
                return
            }
            initialized = true

            appContext = context.applicationContext
            this.config = config

            if (config.enabled && config.showNotification) {
                notificationManager = AProxyINotificationManager(appContext!!, config)
            }

            // AnalyticsInspector has its own switches. Without this they default
            // to on, so a RELEASE config would silence network capture while
            // analytics capture kept running.
            AnalyticsInspector.setEnabled(config.enabled)
            AnalyticsInspector.setLogToLogcat(config.logToLogcat)

            if (config.logToLogcat) {
                Log.d(TAG, "AProxyI initialized (enabled: ${config.enabled})")
            }
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

            sweepStaleRequests()

            val requestId = "req_${requestIdGenerator.incrementAndGet()}_${System.currentTimeMillis()}"
            
            val bodyString = try { BodyFormatter.format(body, config.maxBodySize) } catch (e: Throwable) { null }

            // Redact at capture: secrets never enter the store, so they cannot
            // escape through the UI, share, or copy-as-cURL.
            val request = NetworkRequest(
                id = requestId,
                url = config.redactUrl(url),
                method = method.uppercase(),
                params = config.redactParams(params),
                headers = config.redactHeaders(headers),
                requestBody = bodyString,
                startTime = System.currentTimeMillis(),
                status = RequestStatus.IN_PROGRESS,
                tag = tag
            )
            
            activeRequests[requestId] = request
            activeRequestCount.incrementAndGet()
            totalRequests.incrementAndGet()
            
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
            activeRequestCount.decrementAndGet()
            successfulRequests.incrementAndGet()
            
            val endTime = System.currentTimeMillis()
            
            // Run heavy formatting work on background thread to avoid ANR
            executor.execute {
                try {
                    val responseBody = formatResponse(response)
                    
                    val completedRequest = request.copy(
                        status = RequestStatus.SUCCESS,
                        responseCode = responseCode,
                        responseBody = responseBody,
                        responseHeaders = config.redactHeaders(headers),
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
            activeRequestCount.decrementAndGet()
            failedRequests.incrementAndGet()
            
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
        // Wrapped like every sibling entry point: this class is documented as
        // crash-safe, and callers pass arbitrary ids.
        try {
            if (!config.enabled || requestId.isEmpty()) return

            val request = activeRequests.remove(requestId) ?: return
            activeRequestCount.decrementAndGet()

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

            try { notificationManager?.updateNotification(getStats()) } catch (e: Throwable) { }
            try { notifyListeners() } catch (e: Throwable) { }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onRequestCancelled", e)
        }
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Get all recorded requests (newest first)
     */

    /**
     * Test-only. Blocks until the single-threaded worker has drained.
     *
     * Completion handling is deliberately asynchronous, so a request is not in
     * the store the instant onRequestSuccess/onRequestFailed returns. The
     * executor is FIFO, so a task that completes implies every prior task did.
     */
    internal fun awaitIdleForTesting(timeoutMs: Long = 5_000L): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        executor.execute { latch.countDown() }
        return latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** The active config, so collaborators do not invent their own limits. */
    internal fun currentConfig(): AProxyIConfig = config

    /**
     * Test-only. Drops every piece of state so a fresh [init] can run with a
     * different config. `internal`, so it is not part of the public API the
     * no-op has to mirror.
     */
    internal fun resetForTesting() {
        synchronized(this) {
            synchronized(requests) { requests.clear() }
            activeRequests.clear()
            listeners.clear()
            totalRequests.set(0)
            successfulRequests.set(0)
            failedRequests.set(0)
            activeRequestCount.set(0)
            lastSweepAt.set(0)
            notificationManager = null
            appContext = null
            config = AProxyIConfig.RELEASE
            initialized = false
        }
    }

    internal fun getRequests(): List<NetworkRequest> = synchronized(requests) { requests.toList() }
    
    /**
     * Get requests filtered by status
     */
    internal fun getRequests(status: RequestStatus): List<NetworkRequest> = 
        synchronized(requests) { requests.filter { it.status == status } }
    
    /**
     * Get a specific request by ID
     */
    internal fun getRequest(id: String): NetworkRequest? = 
        synchronized(requests) { requests.find { it.id == id } } ?: activeRequests[id]
    
    /**
     * Search requests by URL or method
     */
    internal fun searchRequests(query: String): List<NetworkRequest> {
        val lowerQuery = query.lowercase()
        return synchronized(requests) { requests.toList() }.filter { request ->
            request.url.lowercase().contains(lowerQuery) ||
            request.method.lowercase().contains(lowerQuery) ||
            request.tag?.lowercase()?.contains(lowerQuery) == true
        }
    }
    
    /**
     * Get current statistics
     */
    internal fun getStats(): RequestStats = RequestStats(
        total = totalRequests.get(),
        active = activeRequestCount.get(),
        successful = successfulRequests.get(),
        failed = failedRequests.get()
    )
    
    /**
     * Clear all recorded requests
     */
    @JvmStatic
    fun clearAll() {
        try {
            synchronized(requests) { requests.clear() }
            // The in-flight map and its counter were previously left behind, so
            // the active count survived a Clear and drifted permanently.
            activeRequests.clear()
            totalRequests.set(0)
            successfulRequests.set(0)
            failedRequests.set(0)
            activeRequestCount.set(0)

            try { notificationManager?.dismiss() } catch (e: Throwable) { }
            try { notifyListeners() } catch (e: Throwable) { }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in clearAll", e)
        }
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
        
        val intent = Intent(context, com.aproxyi.internal.ui.AnalyticsListActivity::class.java).apply {
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
        return Intent(context, com.aproxyi.internal.ui.AnalyticsListActivity::class.java)
    }
    
    // ==================== Listener Methods ====================
    
    /**
     * Add a listener for request updates
     */
    internal fun addListener(listener: RequestListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a listener
     */
    internal fun removeListener(listener: RequestListener) {
        listeners.remove(listener)
    }
    
    // ==================== Internal Methods ====================
    
    private fun addRequest(request: NetworkRequest) {
        // Reachable from the worker thread (success/failed) and from caller
        // threads (cancelled, sweep). Unsynchronised, two threads could both
        // pass the size check and removeAt a stale index, throwing
        // IndexOutOfBoundsException out of a CopyOnWriteArrayList.
        synchronized(requests) {
            try {
                requests.addFirst(request)

                while (requests.size > config.maxRequests) {
                    requests.removeLast()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error storing request", e)
            }
        }
    }

    /**
     * Move in-flight requests that never completed into [RequestStatus.TIMED_OUT].
     *
     * Without this, a code path that forgets to call onRequestSuccess /
     * onRequestFailed leaks its map entry and permanently inflates the active
     * count shown in the notification.
     */
    private fun sweepStaleRequests() {
        try {
            val timeout = config.activeRequestTimeoutMs
            if (timeout <= 0L || activeRequests.isEmpty()) return

            val now = System.currentTimeMillis()
            val last = lastSweepAt.get()
            // At most once a second: this runs on the request path.
            if (now - last < 1_000L) return
            if (!lastSweepAt.compareAndSet(last, now)) return

            var swept = 0
            for ((id, request) in activeRequests.entries.toList()) {
                if (now - request.startTime <= timeout) continue
                // remove() returning null means another thread completed it first.
                if (activeRequests.remove(id) == null) continue

                activeRequestCount.decrementAndGet()
                failedRequests.incrementAndGet()
                swept++

                addRequest(
                    request.copy(
                        status = RequestStatus.TIMED_OUT,
                        endTime = now,
                        duration = now - request.startTime,
                        errorMessage = "No completion call within ${timeout}ms"
                    )
                )
            }

            if (swept > 0) {
                if (config.logToLogcat) {
                    Log.w(TAG, "Swept $swept stale in-flight request(s)")
                }
                try { notificationManager?.updateNotification(getStats()) } catch (e: Throwable) { }
                try { notifyListeners() } catch (e: Throwable) { }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error sweeping stale requests", e)
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
            } catch (e: Throwable) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Listener interface for request updates
     */
    internal interface RequestListener {
        fun onRequestsUpdated(requests: List<NetworkRequest>, stats: RequestStats)
    }
}




