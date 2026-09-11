package com.networkinspector.core

/**
 * Configuration options for NetworkInspector
 */
data class NetworkInspectorConfig(
    /** Whether the inspector is enabled */
    val enabled: Boolean = true,
    
    /** Whether to show notifications */
    val showNotification: Boolean = true,
    
    /** Maximum number of requests to store in memory */
    val maxRequests: Int = 500,
    
    /** Maximum size for request/response bodies (in characters) */
    val maxBodySize: Int = 200_000,
    
    /** Whether to log to Logcat */
    val logToLogcat: Boolean = true,
    
    /** Custom notification channel name */
    val notificationChannelName: String = "Network Inspector",
    
    /** Hosts to exclude from tracking (regex patterns) */
    val excludedHosts: List<String> = emptyList(),
    
    /** Paths to exclude from tracking (regex patterns) */
    val excludedPaths: List<String> = emptyList()
) {
    companion object {
        /** Default configuration for debug builds */
        val DEBUG = NetworkInspectorConfig(
            enabled = true,
            showNotification = true,
            logToLogcat = true
        )
        
        /** Configuration for release builds (disabled) */
        val RELEASE = NetworkInspectorConfig(
            enabled = false,
            showNotification = false,
            logToLogcat = false
        )
    }
    
    /**
     * Check if a URL should be tracked based on exclusion rules
     */
    fun shouldTrack(url: String): Boolean {
        if (!enabled) return false
        
        // Check excluded hosts
        for (pattern in excludedHosts) {
            if (url.contains(Regex(pattern, RegexOption.IGNORE_CASE))) {
                return false
            }
        }
        
        // Check excluded paths
        for (pattern in excludedPaths) {
            if (url.contains(Regex(pattern, RegexOption.IGNORE_CASE))) {
                return false
            }
        }
        
        return true
    }
}




