package com.aproxyi

import android.os.Bundle
import com.aproxyi.core.AnalyticsSource

/**
 * No-op mirror of [com.aproxyi.AnalyticsInspector].
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
}
