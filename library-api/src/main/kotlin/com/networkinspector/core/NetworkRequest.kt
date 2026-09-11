package com.networkinspector.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a tracked network request with all its details.
 * This is the core data model used throughout the library.
 */
data class NetworkRequest(
    /** Unique identifier for this request */
    val id: String,
    
    /** Full URL of the request */
    val url: String,
    
    /** HTTP method (GET, POST, PUT, DELETE, etc.) */
    val method: String,
    
    /** Query parameters */
    val params: Map<String, String>? = null,
    
    /** Request headers */
    val headers: Map<String, String>? = null,
    
    /** Request body (JSON string or raw data) */
    val requestBody: String? = null,
    
    /** Timestamp when request started */
    val startTime: Long,
    
    /** Timestamp when request completed */
    val endTime: Long? = null,
    
    /** Duration in milliseconds */
    val duration: Long? = null,
    
    /** Current status of the request */
    val status: RequestStatus,
    
    /** HTTP response code */
    val responseCode: Int? = null,
    
    /** Response body */
    val responseBody: String? = null,
    
    /** Response headers */
    val responseHeaders: Map<String, String>? = null,
    
    /** Error message if request failed */
    val errorMessage: String? = null,
    
    /** Error stack trace if available */
    val errorStackTrace: String? = null,
    
    /** Custom tag for categorization */
    val tag: String? = null
) {
    /**
     * Returns a short, readable name for display in lists
     */
    val shortName: String
        get() {
            val path = url.substringAfter("://")
                .substringAfter("/")
                .substringBefore("?")
                .split("/")
                .takeLast(2)
                .joinToString("/")
            return "$method /$path"
        }

    /**
     * Returns the host from URL
     */
    val host: String
        get() = try {
            url.substringAfter("://").substringBefore("/").substringBefore("?")
        } catch (e: Exception) {
            url
        }

    /**
     * Returns the path from URL
     */
    val path: String
        get() = try {
            "/" + url.substringAfter("://").substringAfter("/").substringBefore("?")
        } catch (e: Exception) {
            url
        }

    /**
     * Formatted start time for display
     */
    val formattedStartTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(startTime))

    /**
     * Formatted date for display
     */
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(startTime))

    /**
     * Human-readable duration
     */
    val formattedDuration: String
        get() = when {
            duration == null -> "..."
            duration < 1000 -> "${duration}ms"
            else -> String.format(Locale.getDefault(), "%.2fs", duration / 1000.0)
        }

    /**
     * Returns formatted request size
     */
    val requestSize: String
        get() = formatBytes(requestBody?.length?.toLong() ?: 0)

    /**
     * Returns formatted response size
     */
    val responseSize: String
        get() = formatBytes(responseBody?.length?.toLong() ?: 0)

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * Status of a network request
 */
enum class RequestStatus {
    /** Request is currently in progress */
    IN_PROGRESS,
    
    /** Request completed successfully */
    SUCCESS,
    
    /** Request failed with an error */
    FAILED,
    
    /** Request was cancelled */
    CANCELLED
}

/**
 * Statistics about tracked requests
 */
data class RequestStats(
    val total: Int,
    val active: Int,
    val successful: Int,
    val failed: Int
) {
    val completed: Int get() = successful + failed
}




