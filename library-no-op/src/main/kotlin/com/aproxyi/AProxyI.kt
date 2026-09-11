package com.aproxyi

import android.content.Context
import android.content.Intent
import com.aproxyi.core.AProxyIConfig

/**
 * No-op mirror of [com.aproxyi.AProxyI].
 *
 * Linked into release builds via `releaseImplementation`. Every method is
 * empty, so nothing is captured, stored, logged or displayed in a shipped
 * build -- the capture code is not merely disabled, it is absent from the APK.
 *
 * ## Parity contract
 * This object must expose exactly the same public signatures as the real one in
 * `:library`. Binary Compatibility Validator checks this: run `./gradlew
 * apiDump` after changing either side, and CI fails if the two drift.
 */
object AProxyI {

    @JvmStatic
    fun init(context: Context) {
        // No-op.
    }

    @JvmStatic
    fun init(context: Context, config: AProxyIConfig) {
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
}
