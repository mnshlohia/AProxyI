package com.networkinspector.interceptor

import android.util.Log
import com.networkinspector.NetworkInspector

/**
 * A callback interceptor that wraps your existing callbacks to automatically
 * track network requests with NetworkInspector.
 * 
 * All methods are crash-safe and will never throw exceptions to the caller.
 */
class CallbackInterceptor<T> private constructor(
    private val requestId: String
) {
    
    /**
     * Call this when the request succeeds
     */
    @JvmOverloads
    fun onSuccess(
        responseCode: Int = 200,
        response: T? = null,
        headers: Map<String, String>? = null
    ) {
        try {
            NetworkInspector.onRequestSuccess(requestId, responseCode, response, headers)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onSuccess", e)
        }
    }
    
    /**
     * Call this when the request fails
     */
    @JvmOverloads
    fun onFailure(
        responseCode: Int = 0,
        error: Any? = null
    ) {
        try {
            NetworkInspector.onRequestFailed(requestId, responseCode, error)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onFailure", e)
        }
    }
    
    /**
     * Call this when the request is cancelled
     */
    fun onCancelled() {
        try {
            NetworkInspector.onRequestCancelled(requestId)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onCancelled", e)
        }
    }
    
    /**
     * Get the request ID for manual tracking
     */
    fun getRequestId(): String = requestId
    
    companion object {
        private const val TAG = "CallbackInterceptor"
        
        /**
         * Create a new callback interceptor and start tracking.
         */
        @JvmStatic
        @JvmOverloads
        fun <T> create(
            url: String,
            method: String = "GET",
            params: Map<String, String>? = null,
            headers: Map<String, String>? = null,
            body: Any? = null,
            tag: String? = null
        ): CallbackInterceptor<T> {
            return try {
                // Pass the raw body through: onRequestStart formats it, so
                // formatting here as well did the expensive work twice.
                val requestId = NetworkInspector.onRequestStart(
                    url = url,
                    method = method,
                    params = params,
                    headers = headers,
                    body = body,
                    tag = tag
                )
                
                CallbackInterceptor(requestId)
            } catch (e: Throwable) {
                Log.e(TAG, "Error creating interceptor", e)
                CallbackInterceptor("")
            }
        }
        
        /**
         * Create interceptor from a builder for more options
         */
        @JvmStatic
        fun <T> builder(): Builder<T> = Builder()
    }
    
    /**
     * Builder for creating CallbackInterceptor with fluent API
     */
    class Builder<T> {
        private var url: String = ""
        private var method: String = "GET"
        private var params: Map<String, String>? = null
        private var headers: Map<String, String>? = null
        private var body: Any? = null
        private var tag: String? = null
        private var baseUrl: String? = null
        
        fun url(url: String) = apply { this.url = url }
        fun baseUrl(baseUrl: String) = apply { this.baseUrl = baseUrl }
        fun method(method: String) = apply { this.method = method }
        fun get() = apply { this.method = "GET" }
        fun post() = apply { this.method = "POST" }
        fun put() = apply { this.method = "PUT" }
        fun delete() = apply { this.method = "DELETE" }
        fun patch() = apply { this.method = "PATCH" }
        fun params(params: Map<String, String>?) = apply { this.params = params }
        fun headers(headers: Map<String, String>?) = apply { this.headers = headers }
        fun addHeader(key: String, value: String) = apply {
            this.headers = (this.headers ?: mutableMapOf()) + (key to value)
        }
        fun body(body: Any?) = apply { this.body = body }
        fun tag(tag: String) = apply { this.tag = tag }
        
        fun build(): CallbackInterceptor<T> {
            return try {
                val fullUrl = when {
                    url.startsWith("http") -> url
                    baseUrl != null -> baseUrl + url
                    else -> url
                }
                
                create(
                    url = fullUrl,
                    method = method,
                    params = params,
                    headers = headers,
                    body = body,
                    tag = tag
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error building interceptor", e)
                CallbackInterceptor("")
            }
        }
    }
}

/**
 * Extension function to wrap any callback with NetworkInspector tracking
 */
inline fun <T> trackRequest(
    url: String,
    method: String = "GET",
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    body: Any? = null,
    crossinline onSuccess: (Int, T?) -> Unit,
    crossinline onFailure: (Int, Any?) -> Unit
): Pair<(Int, T?, Map<String, String>?) -> Unit, (Int, Any?) -> Unit> {
    val interceptor = CallbackInterceptor.create<T>(url, method, params, headers, body)
    
    val successCallback: (Int, T?, Map<String, String>?) -> Unit = { code, response, respHeaders ->
        try { interceptor.onSuccess(code, response, respHeaders) } catch (e: Throwable) { }
        onSuccess(code, response)
    }
    
    val failureCallback: (Int, Any?) -> Unit = { code, error ->
        try { interceptor.onFailure(code, error) } catch (e: Throwable) { }
        onFailure(code, error)
    }
    
    return Pair(successCallback, failureCallback)
}
