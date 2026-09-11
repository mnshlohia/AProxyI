package com.networkinspector.interceptor

import android.util.Log
import com.networkinspector.NetworkInspector
import com.networkinspector.util.BodyFormatter
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * OkHttp Interceptor that automatically tracks all network requests with NetworkInspector.
 * 
 * This interceptor is crash-safe - any internal errors are caught and logged,
 * and the original request/response flow is never interrupted.
 */
class NetworkInspectorInterceptor @JvmOverloads constructor(
    private val maxContentLength: Long = 250_000L,
    private val headersToRedact: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
) : Interceptor {
    
    companion object {
        private const val TAG = "NetworkInspectorInterceptor"
        private const val CONTENT_TYPE = "Content-Type"
        private const val CONTENT_LENGTH = "Content-Length"
        private const val CONTENT_ENCODING = "Content-Encoding"
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var requestId = ""
        
        // Try to start tracking - never let this fail the request
        try {
            val url = request.url.toString()
            val method = request.method
            val requestHeaders = try { extractHeaders(request.headers) } catch (e: Throwable) { emptyMap() }
            val requestBody = try { extractRequestBody(request) } catch (e: Throwable) { null }
            
            requestId = NetworkInspector.onRequestStart(
                url = url,
                method = method,
                headers = requestHeaders,
                body = requestBody
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting request tracking", e)
        }
        
        // Execute request - this is the only part that can throw
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            // Track failure but don't let tracking fail the error propagation
            try {
                if (requestId.isNotEmpty()) {
                    NetworkInspector.onRequestFailed(requestId, 0, e)
                }
            } catch (trackingError: Throwable) {
                Log.e(TAG, "Error tracking request failure", trackingError)
            }
            throw e
        }
        
        // Try to track response - never let this fail the response
        try {
            if (requestId.isNotEmpty()) {
                val responseCode = response.code
                val responseHeaders = try { extractHeaders(response.headers) } catch (e: Throwable) { emptyMap() }
                val responseBody = try { extractResponseBody(response) } catch (e: Throwable) { null }
                
                if (responseCode in 200..299) {
                    NetworkInspector.onRequestSuccess(
                        requestId = requestId,
                        responseCode = responseCode,
                        response = responseBody,
                        headers = responseHeaders
                    )
                } else {
                    NetworkInspector.onRequestFailed(
                        requestId = requestId,
                        responseCode = responseCode,
                        error = responseBody ?: "HTTP $responseCode"
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error tracking response", e)
        }
        
        return response
    }
    
    /**
     * Extract headers as a map, redacting sensitive values
     */
    private fun extractHeaders(headers: Headers): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            for (i in 0 until headers.size) {
                val name = headers.name(i)
                val value = if (headersToRedact.any { it.equals(name, ignoreCase = true) }) {
                    "██████████"
                } else {
                    headers.value(i)
                }
                result[name] = value
            }
        } catch (e: Throwable) {
            // Ignore
        }
        return result
    }
    
    /**
     * Extract request body as string
     */
    private fun extractRequestBody(request: Request): String? {
        return try {
            val body = request.body ?: return null
            
            if (bodyHasUnknownEncoding(request.headers)) {
                return "[encoded body omitted]"
            }
            
            if (body.isDuplex()) {
                return "[duplex request body omitted]"
            }
            
            if (body.isOneShot()) {
                return "[one-shot body omitted]"
            }
            
            val contentLength = body.contentLength()
            if (contentLength > maxContentLength) {
                return "[body too large: $contentLength bytes]"
            }
            
            val buffer = Buffer()
            body.writeTo(buffer)
            
            val contentType = body.contentType()
            val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) 
                ?: StandardCharsets.UTF_8
            
            if (buffer.isProbablyUtf8()) {
                val content = buffer.readString(charset)
                BodyFormatter.format(content, maxContentLength.toInt())
            } else {
                "[binary body: ${buffer.size} bytes]"
            }
        } catch (e: Throwable) {
            "[error reading body]"
        }
    }
    
    /**
     * Extract response body as string (without consuming it)
     */
    private fun extractResponseBody(response: Response): String? {
        return try {
            val body = response.body ?: return null
            
            if (bodyHasUnknownEncoding(response.headers)) {
                return "[encoded body omitted]"
            }
            
            val contentLength = body.contentLength()
            if (contentLength > maxContentLength) {
                return "[body too large: $contentLength bytes]"
            }
            
            val source = body.source()
            source.request(Long.MAX_VALUE)
            var buffer = source.buffer.clone()
            
            // Handle gzip
            if ("gzip".equals(response.headers[CONTENT_ENCODING], ignoreCase = true)) {
                GzipSource(buffer.clone()).use { gzippedSource ->
                    buffer = Buffer()
                    buffer.writeAll(gzippedSource)
                }
            }
            
            val contentType = body.contentType()
            val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) 
                ?: StandardCharsets.UTF_8
            
            if (buffer.size != 0L && buffer.isProbablyUtf8()) {
                val content = buffer.readString(charset)
                BodyFormatter.format(content, maxContentLength.toInt())
            } else if (buffer.size != 0L) {
                "[binary body: ${buffer.size} bytes]"
            } else {
                null
            }
        } catch (e: Throwable) {
            "[error reading body]"
        }
    }
    
    private fun bodyHasUnknownEncoding(headers: Headers): Boolean {
        return try {
            val contentEncoding = headers[CONTENT_ENCODING] ?: return false
            !contentEncoding.equals("identity", ignoreCase = true) &&
                    !contentEncoding.equals("gzip", ignoreCase = true)
        } catch (e: Throwable) {
            false
        }
    }
    
    /**
     * Check if buffer contains UTF-8 text
     */
    private fun Buffer.isProbablyUtf8(): Boolean {
        return try {
            val prefix = Buffer()
            val byteCount = size.coerceAtMost(64)
            copyTo(prefix, 0, byteCount)
            for (i in 0 until 16) {
                if (prefix.exhausted()) break
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            true
        } catch (e: Throwable) {
            false
        }
    }
}
