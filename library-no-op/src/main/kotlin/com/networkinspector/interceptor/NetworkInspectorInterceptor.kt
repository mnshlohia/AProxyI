package com.networkinspector.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * No-op mirror of [com.networkinspector.interceptor.NetworkInspectorInterceptor].
 *
 * Passes the chain straight through, so leaving it registered on a release
 * OkHttpClient costs one virtual call, never reads a body, and captures
 * nothing. The constructor parameters are kept only for signature parity.
 */
class NetworkInspectorInterceptor @JvmOverloads constructor(
    private val maxContentLength: Long = 250_000L,
    private val headersToRedact: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie")
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request())
}
