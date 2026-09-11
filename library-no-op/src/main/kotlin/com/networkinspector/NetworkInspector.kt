package com.networkinspector

import android.content.Context
import android.content.Intent
import com.networkinspector.core.NetworkInspectorConfig
import com.networkinspector.core.NetworkRequest
import com.networkinspector.core.RequestStats
import com.networkinspector.core.RequestStatus

/**
 * No-op mirror of [com.networkinspector.NetworkInspector].
 *
 * Linked into release builds via `releaseImplementation`. Every method is
 * empty, so nothing is captured, stored, logged or displayed in a shipped
 * build -- the capture code is not merely disabled, it is absent from the APK.
 *
 * ## Parity contract
 * This object must expose exactly the same public signatures as the real one
 * in `:library`. Adding a public method there without adding the stub here
 * breaks the consumer's *release* build -- loudly, at compile time, which is
 * the intended failure mode.
 */
object NetworkInspector {

    @JvmStatic
    fun init(context: Context) {
        // No-op.
    }

    @JvmStatic
    fun init(context: Context, config: NetworkInspectorConfig) {
        // No-op.
    }

    /** Always false here, so callers can branch without a build-variant check. */
    @JvmStatic
    fun isEnabled(): Boolean = false

    @JvmStatic
    @JvmOverloads
    fun onRequestStart(
        url: String,
        method: String = "GET",
        params: Map<String, String>? = null,
        headers: Map<String, String>? = null,
        body: Any? = null,
        tag: String? = null
    ): String = ""

    @JvmStatic
    @JvmOverloads
    fun onRequestSuccess(
        requestId: String,
        responseCode: Int = 200,
        response: Any? = null,
        headers: Map<String, String>? = null
    ) {
        // No-op.
    }

    @JvmStatic
    @JvmOverloads
    fun onRequestFailed(
        requestId: String,
        responseCode: Int = 0,
        error: Any? = null
    ) {
        // No-op.
    }

    @JvmStatic
    fun onRequestCancelled(requestId: String) {
        // No-op.
    }

    @JvmStatic
    fun getRequests(): List<NetworkRequest> = emptyList()

    @JvmStatic
    fun getRequests(status: RequestStatus): List<NetworkRequest> = emptyList()

    @JvmStatic
    fun getRequest(id: String): NetworkRequest? = null

    @JvmStatic
    fun searchRequests(query: String): List<NetworkRequest> = emptyList()

    @JvmStatic
    fun getStats(): RequestStats = RequestStats(
        total = 0,
        active = 0,
        successful = 0,
        failed = 0
    )

    @JvmStatic
    fun clearAll() {
        // No-op.
    }

    @JvmStatic
    fun launch(context: Context) {
        // No-op: there is no inspector UI in a release build.
    }

    @JvmStatic
    fun launchAnalytics(context: Context) {
        // No-op.
    }

    /**
     * Returns an intent that resolves to nothing. Kept for signature parity;
     * starting it is a no-op because no activity is declared in this artifact.
     */
    @JvmStatic
    fun getLaunchIntent(context: Context): Intent = Intent()

    @JvmStatic
    fun getAnalyticsLaunchIntent(context: Context): Intent = Intent()

    @JvmStatic
    fun addListener(listener: RequestListener) {
        // No-op: listeners are never invoked.
    }

    @JvmStatic
    fun removeListener(listener: RequestListener) {
        // No-op.
    }

    interface RequestListener {
        fun onRequestsUpdated(requests: List<NetworkRequest>, stats: RequestStats)
    }
}
