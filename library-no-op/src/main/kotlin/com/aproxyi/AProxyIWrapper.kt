package com.aproxyi

import android.content.Context
import com.aproxyi.core.AProxyIConfig

/**
 * No-op mirror of [com.aproxyi.AProxyIWrapper].
 *
 * The `inline` functions below must stay `inline` here too: the real artifact
 * declares them inline, so their bodies are baked into consumer bytecode at
 * compile time. A non-inline stub would change the consumer's call shape
 * between debug and release.
 *
 * Each one still invokes the caller's `block`, so the actual network call
 * happens normally -- only the tracking around it disappears.
 */
object AProxyIWrapper {

    @JvmStatic
    fun init(context: Context, config: AProxyIConfig = AProxyIConfig.DEBUG) {
        // No-op.
    }

    @JvmStatic
    fun isEnabled(): Boolean = false

    @JvmStatic
    fun request(): RequestBuilder = RequestBuilder()

    @JvmStatic
    inline fun <T> track(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        block: () -> T
    ): T = block()

    @JvmStatic
    inline fun <T> trackWithCode(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        block: () -> Pair<Int, T>
    ): T = block().second

    suspend inline fun <T> trackSuspend(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        crossinline block: suspend () -> T
    ): T = block()

    suspend inline fun <T> trackSuspendWithCode(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: Any? = null,
        crossinline block: suspend () -> Pair<Int, T>
    ): T = block().second

    @JvmStatic
    fun clear() = AProxyI.clearAll()

    @JvmStatic
    fun launch(context: Context) = AProxyI.launch(context)

    class RequestBuilder {
        fun url(url: String) = apply { }
        fun method(method: String) = apply { }
        fun get() = apply { }
        fun post() = apply { }
        fun put() = apply { }
        fun delete() = apply { }
        fun patch() = apply { }
        fun params(params: Map<String, String>) = apply { }
        fun headers(headers: Map<String, String>) = apply { }
        fun header(key: String, value: String) = apply { }
        fun body(body: Any?) = apply { }
        fun tag(tag: String) = apply { }

        fun start(): RequestTracker = RequestTracker("")

        inline fun <T> execute(block: () -> T): T = block()

        suspend inline fun <T> executeSuspend(crossinline block: suspend () -> T): T = block()
    }

    class RequestTracker(private val requestId: String) {

        @JvmOverloads
        fun success(
            responseCode: Int = 200,
            response: Any? = null,
            headers: Map<String, String>? = null
        ) {
            // No-op.
        }

        @JvmOverloads
        fun failed(
            responseCode: Int = 0,
            error: Any? = null
        ) {
            // No-op.
        }

        fun failed(exception: Throwable) {
            // No-op.
        }

        fun cancelled() {
            // No-op.
        }
    }
}
