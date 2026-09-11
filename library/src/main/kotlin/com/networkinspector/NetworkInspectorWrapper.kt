package com.networkinspector

import android.content.Context
import com.networkinspector.core.NetworkInspectorConfig
import com.networkinspector.core.NetworkRequest
import com.networkinspector.core.RequestStats

/**
 * A convenient wrapper class for NetworkInspector that provides a fluent API
 * for tracking network requests.
 * 
 * ## Usage:
 * 
 * ### Option 1: Simple tracking with callbacks
 * ```kotlin
 * NetworkInspectorWrapper.track("https://api.example.com/users", "GET") {
 *     // Make your network call here
 *     val response = apiService.getUsers()
 *     response
 * }
 * ```
 * 
 * ### Option 2: Builder pattern for more control
 * ```kotlin
 * val tracker = NetworkInspectorWrapper.request()
 *     .url("https://api.example.com/users")
 *     .method("POST")
 *     .headers(mapOf("Authorization" to "Bearer token"))
 *     .body(requestBody)
 *     .start()
 *     
 * try {
 *     val response = apiService.createUser(user)
 *     tracker.success(200, response)
 * } catch (e: Exception) {
 *     tracker.failed(e)
 * }
 * ```
 * 
 * ### Option 3: Using with suspend functions
 * ```kotlin
 * NetworkInspectorWrapper.trackSuspend("https://api.example.com/users", "GET") {
 *     apiService.getUsers()
 * }
 * ```
 */
object NetworkInspectorWrapper {
    
    /**
     * Initialize the NetworkInspector. Call this in your Application.onCreate()
     */
    @JvmStatic
    fun init(context: Context, config: NetworkInspectorConfig = NetworkInspectorConfig.DEBUG) {
        NetworkInspector.init(context, config)
    }
    
    /**
     * Check if inspector is enabled
     */
    @JvmStatic
    fun isEnabled(): Boolean = NetworkInspector.isEnabled()
    
    /**
     * Create a new request builder for fluent API
     */
    @JvmStatic
    fun request(): RequestBuilder = RequestBuilder()
    
    /**
     * Track a synchronous network call with automatic success/failure handling
     * 
     * @param url The URL being called
     * @param method HTTP method (GET, POST, etc.)
     * @param headers Optional request headers
     * @param body Optional request body
     * @param block The code block that makes the actual network call
     * @return The result of the network call
     */
    @JvmStatic
    inline fun <T> track(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        block: () -> T
    ): T {
        val requestId = NetworkInspector.onRequestStart(
            url = url,
            method = method,
            headers = headers,
            body = body
        )
        
        return try {
            val result = block()
            NetworkInspector.onRequestSuccess(requestId, 200, result)
            result
        } catch (e: Exception) {
            NetworkInspector.onRequestFailed(requestId, 0, e)
            throw e
        }
    }
    
    /**
     * Track a synchronous network call with response code
     */
    @JvmStatic
    inline fun <T> trackWithCode(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        block: () -> Pair<Int, T>
    ): T {
        val requestId = NetworkInspector.onRequestStart(
            url = url,
            method = method,
            headers = headers,
            body = body
        )
        
        return try {
            val (code, result) = block()
            if (code in 200..299) {
                NetworkInspector.onRequestSuccess(requestId, code, result)
            } else {
                NetworkInspector.onRequestFailed(requestId, code, result)
            }
            result
        } catch (e: Exception) {
            NetworkInspector.onRequestFailed(requestId, 0, e)
            throw e
        }
    }
    
    /**
     * Track a suspend (coroutine) network call
     */
    suspend inline fun <T> trackSuspend(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        crossinline block: suspend () -> T
    ): T {
        val requestId = NetworkInspector.onRequestStart(
            url = url,
            method = method,
            headers = headers,
            body = body
        )
        
        return try {
            val result = block()
            NetworkInspector.onRequestSuccess(requestId, 200, result)
            result
        } catch (e: Exception) {
            NetworkInspector.onRequestFailed(requestId, 0, e)
            throw e
        }
    }
    
    /**
     * Track a suspend network call with response code
     */
    suspend inline fun <T> trackSuspendWithCode(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        crossinline block: suspend () -> Pair<Int, T>
    ): T {
        val requestId = NetworkInspector.onRequestStart(
            url = url,
            method = method,
            headers = headers,
            body = body
        )
        
        return try {
            val (code, result) = block()
            if (code in 200..299) {
                NetworkInspector.onRequestSuccess(requestId, code, result)
            } else {
                NetworkInspector.onRequestFailed(requestId, code, result)
            }
            result
        } catch (e: Exception) {
            NetworkInspector.onRequestFailed(requestId, 0, e)
            throw e
        }
    }
    
    /**
     * Get all recorded requests
     */
    @JvmStatic
    fun getRequests(): List<NetworkRequest> = NetworkInspector.getRequests()
    
    /**
     * Get current statistics
     */
    @JvmStatic
    fun getStats(): RequestStats = NetworkInspector.getStats()
    
    /**
     * Search requests by URL or method
     */
    @JvmStatic
    fun search(query: String): List<NetworkRequest> = NetworkInspector.searchRequests(query)
    
    /**
     * Clear all recorded requests
     */
    @JvmStatic
    fun clear() = NetworkInspector.clearAll()
    
    /**
     * Launch the inspector UI
     */
    @JvmStatic
    fun launch(context: Context) = NetworkInspector.launch(context)
    
    /**
     * Add a listener for request updates
     */
    @JvmStatic
    fun addListener(listener: NetworkInspector.RequestListener) = 
        NetworkInspector.addListener(listener)
    
    /**
     * Remove a listener
     */
    @JvmStatic
    fun removeListener(listener: NetworkInspector.RequestListener) = 
        NetworkInspector.removeListener(listener)
    
    /**
     * Builder class for creating and tracking requests with a fluent API
     */
    class RequestBuilder {
        private var url: String = ""
        private var method: String = "GET"
        private var params: Map<String, String>? = null
        private var headers: Map<String, String>? = null
        private var body: Any? = null
        private var tag: String? = null
        
        fun url(url: String) = apply { this.url = url }
        fun method(method: String) = apply { this.method = method }
        fun get() = apply { this.method = "GET" }
        fun post() = apply { this.method = "POST" }
        fun put() = apply { this.method = "PUT" }
        fun delete() = apply { this.method = "DELETE" }
        fun patch() = apply { this.method = "PATCH" }
        fun params(params: Map<String, String>) = apply { this.params = params }
        fun headers(headers: Map<String, String>) = apply { this.headers = headers }
        fun header(key: String, value: String) = apply { 
            this.headers = (this.headers ?: emptyMap()) + (key to value)
        }
        fun body(body: Any?) = apply { this.body = body }
        fun tag(tag: String) = apply { this.tag = tag }
        
        /**
         * Start tracking the request. Returns a RequestTracker to complete the tracking.
         */
        fun start(): RequestTracker {
            val requestId = NetworkInspector.onRequestStart(
                url = url,
                method = method,
                params = params,
                headers = headers,
                body = body,
                tag = tag
            )
            return RequestTracker(requestId)
        }
        
        /**
         * Execute a block and automatically track success/failure
         */
        inline fun <T> execute(block: () -> T): T {
            val tracker = start()
            return try {
                val result = block()
                tracker.success(200, result)
                result
            } catch (e: Exception) {
                tracker.failed(e)
                throw e
            }
        }
        
        /**
         * Execute a suspend block and automatically track success/failure
         */
        suspend inline fun <T> executeSuspend(crossinline block: suspend () -> T): T {
            val tracker = start()
            return try {
                val result = block()
                tracker.success(200, result)
                result
            } catch (e: Exception) {
                tracker.failed(e)
                throw e
            }
        }
    }
    
    /**
     * Tracker instance for completing request tracking
     */
    class RequestTracker(private val requestId: String) {
        
        /**
         * Mark request as successful
         */
        @JvmOverloads
        fun success(
            responseCode: Int = 200,
            response: Any? = null,
            headers: Map<String, String>? = null
        ) {
            NetworkInspector.onRequestSuccess(requestId, responseCode, response, headers)
        }
        
        /**
         * Mark request as failed
         */
        @JvmOverloads
        fun failed(
            responseCode: Int = 0,
            error: Any? = null
        ) {
            NetworkInspector.onRequestFailed(requestId, responseCode, error)
        }
        
        /**
         * Mark request as failed with exception
         */
        fun failed(exception: Throwable) {
            NetworkInspector.onRequestFailed(requestId, 0, exception)
        }
        
        /**
         * Mark request as cancelled
         */
        fun cancelled() {
            NetworkInspector.onRequestCancelled(requestId)
        }
    }
}





