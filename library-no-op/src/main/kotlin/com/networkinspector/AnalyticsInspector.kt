package com.networkinspector

import android.os.Bundle
import com.networkinspector.core.AnalyticsEvent
import com.networkinspector.core.AnalyticsSource

/**
 * No-op mirror of [com.networkinspector.AnalyticsInspector].
 *
 * Analytics payloads carry user identifiers and behavioural data, so in a
 * release build there is nothing here to capture or retain them.
 */
object AnalyticsInspector {

    @JvmStatic
    fun logEvent(
        eventName: String,
        params: Bundle?,
        source: AnalyticsSource = AnalyticsSource.FIREBASE
    ) {
        // No-op.
    }

    @JvmStatic
    fun logEvent(
        eventName: String,
        params: Map<String, Any?>?,
        source: AnalyticsSource = AnalyticsSource.FIREBASE
    ) {
        // No-op.
    }

    @JvmStatic
    fun getEvents(): List<AnalyticsEvent> = emptyList()

    @JvmStatic
    fun getEvents(source: AnalyticsSource): List<AnalyticsEvent> = emptyList()

    @JvmStatic
    fun searchEvents(query: String): List<AnalyticsEvent> = emptyList()

    @JvmStatic
    fun getEvent(id: String): AnalyticsEvent? = null

    @JvmStatic
    fun getEventCount(): Int = 0

    @JvmStatic
    fun clearAll() {
        // No-op.
    }

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        // No-op: cannot be enabled in a release build, by construction.
    }

    @JvmStatic
    fun setLogToLogcat(log: Boolean) {
        // No-op.
    }

    @JvmStatic
    fun addListener(listener: EventListener) {
        // No-op.
    }

    @JvmStatic
    fun removeListener(listener: EventListener) {
        // No-op.
    }

    interface EventListener {
        fun onEventsUpdated(events: List<AnalyticsEvent>)
    }
}
