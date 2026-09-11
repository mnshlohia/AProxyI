package com.networkinspector.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.networkinspector.AnalyticsInspector
import com.networkinspector.NetworkInspector
import com.networkinspector.NetworkInspectorWrapper
import com.networkinspector.core.AnalyticsSource
import com.networkinspector.interceptor.CallbackInterceptor
import com.networkinspector.interceptor.NetworkInspectorInterceptor
import com.networkinspector.interceptor.trackRequest
import okhttp3.OkHttpClient

/**
 * Exercises every public entry point, so that `:sample:assembleRelease` fails if
 * `:library` and `:library-no-op` ever drift apart.
 */
class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkInspectorInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(Button(this@MainActivity).apply {
                    text = "Open inspector"
                    setOnClickListener { NetworkInspector.launch(this@MainActivity) }
                })
                addView(Button(this@MainActivity).apply {
                    text = "Open analytics"
                    setOnClickListener { NetworkInspector.launchAnalytics(this@MainActivity) }
                })
                addView(Button(this@MainActivity).apply {
                    text = "Exercise API"
                    setOnClickListener { exerciseApi() }
                })
            }
        )
    }

    private fun exerciseApi() {
        // Callback API.
        val id = NetworkInspector.onRequestStart(
            url = "https://example.com/v1/orders?access_token=SHOULD_BE_REDACTED",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer SHOULD_BE_REDACTED"),
            body = """{"item":1}"""
        )
        NetworkInspector.onRequestSuccess(id, 200, """{"ok":true}""")

        val cancelled = NetworkInspector.onRequestStart("https://example.com/slow")
        NetworkInspector.onRequestCancelled(cancelled)

        val failed = NetworkInspector.onRequestStart("https://example.com/boom")
        NetworkInspector.onRequestFailed(failed, 500, IllegalStateException("boom"))

        // Fluent wrapper.
        NetworkInspectorWrapper.track("https://example.com/wrapped") { "body" }
        NetworkInspectorWrapper.trackWithCode("https://example.com/coded") { 200 to "body" }
        NetworkInspectorWrapper.request()
            .url("https://example.com/built")
            .post()
            .header("X-Api-Key", "SHOULD_BE_REDACTED")
            .body("payload")
            .execute { "done" }
        NetworkInspectorWrapper.request().url("https://example.com/tracked").start().success(201)

        // Callback interceptor.
        val interceptor = CallbackInterceptor.create<String>("https://example.com/cb")
        interceptor.onSuccess(200, "ok")
        CallbackInterceptor.builder<String>().url("https://example.com/b").get().build().onCancelled()
        trackRequest<String>(
            url = "https://example.com/top",
            onSuccess = { _, _ -> },
            onFailure = { _, _ -> }
        )

        // Analytics.
        AnalyticsInspector.logEvent("add_to_cart", mapOf("sku" to "123"), AnalyticsSource.FIREBASE)
        AnalyticsInspector.logEvent("purchase", null as Map<String, Any?>?, AnalyticsSource.CLEVERTAP)

        NetworkInspector.isEnabled()
        NetworkInspector.getLaunchIntent(this)
        NetworkInspector.getAnalyticsLaunchIntent(this)
    }
}
