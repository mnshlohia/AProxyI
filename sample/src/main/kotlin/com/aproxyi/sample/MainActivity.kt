package com.aproxyi.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.aproxyi.AnalyticsInspector
import com.aproxyi.AProxyI
import com.aproxyi.AProxyIWrapper
import com.aproxyi.core.AnalyticsSource
import com.aproxyi.interceptor.CallbackInterceptor
import com.aproxyi.interceptor.AProxyIInterceptor
import com.aproxyi.interceptor.trackRequest
import okhttp3.OkHttpClient

/**
 * Exercises every public entry point, so that `:sample:assembleRelease` fails if
 * `:library` and `:library-no-op` ever drift apart.
 */
class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .addInterceptor(AProxyIInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(Button(this@MainActivity).apply {
                    text = "Open inspector"
                    setOnClickListener { AProxyI.launch(this@MainActivity) }
                })
                addView(Button(this@MainActivity).apply {
                    text = "Open analytics"
                    setOnClickListener { AProxyI.launchAnalytics(this@MainActivity) }
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
        val id = AProxyI.onRequestStart(
            url = "https://example.com/v1/orders?access_token=SHOULD_BE_REDACTED",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer SHOULD_BE_REDACTED"),
            body = """{"item":1}"""
        )
        AProxyI.onRequestSuccess(id, 200, """{"ok":true}""")

        val cancelled = AProxyI.onRequestStart("https://example.com/slow")
        AProxyI.onRequestCancelled(cancelled)

        val failed = AProxyI.onRequestStart("https://example.com/boom")
        AProxyI.onRequestFailed(failed, 500, IllegalStateException("boom"))

        // Fluent wrapper.
        AProxyIWrapper.track("https://example.com/wrapped") { "body" }
        AProxyIWrapper.trackWithCode("https://example.com/coded") { 200 to "body" }
        AProxyIWrapper.request()
            .url("https://example.com/built")
            .post()
            .header("X-Api-Key", "SHOULD_BE_REDACTED")
            .body("payload")
            .execute { "done" }
        AProxyIWrapper.request().url("https://example.com/tracked").start().success(201)

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

        AProxyI.isEnabled()
        AProxyI.getLaunchIntent(this)
        AProxyI.getAnalyticsLaunchIntent(this)
    }
}
