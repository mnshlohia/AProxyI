package com.aproxyi.core

/**
 * Represents a logged analytics event
 */
data class AnalyticsEvent(
    val id: String,
    val eventName: String,
    val params: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis(),
    val source: AnalyticsSource = AnalyticsSource.FIREBASE
) {
    
    val formattedTime: String
        get() {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }
    
    val paramsCount: Int
        get() = params.size
    
    fun matchesSearch(query: String): Boolean {
        val lowerQuery = query.lowercase()
        return eventName.lowercase().contains(lowerQuery) ||
               params.keys.any { it.lowercase().contains(lowerQuery) } ||
               params.values.any { it.lowercase().contains(lowerQuery) }
    }
}

enum class AnalyticsSource {
    FIREBASE,
    CLEVERTAP,
    APPSFLYER,
    FACEBOOK
}

