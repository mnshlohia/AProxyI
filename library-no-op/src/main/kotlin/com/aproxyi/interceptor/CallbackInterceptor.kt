package com.aproxyi.interceptor

/**
 * No-op mirror of [com.aproxyi.interceptor.CallbackInterceptor].
 *
 * Every callback hook is empty, so wrapping your existing callbacks costs
 * nothing in a release build and records nothing.
 */
class CallbackInterceptor<T> private constructor(
    private val requestId: String
) {

    @JvmOverloads
    fun onSuccess(
        responseCode: Int = 200,
        response: T? = null,
        headers: Map<String, String>? = null
    ) {
        // No-op.
    }

    @JvmOverloads
    fun onFailure(
        responseCode: Int = 0,
        error: Any? = null
    ) {
        // No-op.
    }

    fun onCancelled() {
        // No-op.
    }

    fun getRequestId(): String = requestId

    companion object {
        @JvmStatic
        @JvmOverloads
        fun <T> create(
            url: String,
            method: String = "GET",
            params: Map<String, String>? = null,
            headers: Map<String, String>? = null,
            body: Any? = null,
            tag: String? = null
        ): CallbackInterceptor<T> = CallbackInterceptor("")

        @JvmStatic
        fun <T> builder(): Builder<T> = Builder()
    }

    class Builder<T> {
        fun url(url: String) = apply { }
        fun baseUrl(baseUrl: String) = apply { }
        fun method(method: String) = apply { }
        fun get() = apply { }
        fun post() = apply { }
        fun put() = apply { }
        fun delete() = apply { }
        fun patch() = apply { }
        fun params(params: Map<String, String>?) = apply { }
        fun headers(headers: Map<String, String>?) = apply { }
        fun addHeader(key: String, value: String) = apply { }
        fun body(body: Any?) = apply { }
        fun tag(tag: String) = apply { }

        fun build(): CallbackInterceptor<T> = CallbackInterceptor("")
    }
}

/**
 * No-op mirror of the real `trackRequest`. Declared `inline` to match the real
 * artifact. The caller's callbacks are still invoked -- only the recording
 * around them is gone.
 */
inline fun <T> trackRequest(
    url: String,
    method: String = "GET",
    params: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    body: Any? = null,
    crossinline onSuccess: (Int, T?) -> Unit,
    crossinline onFailure: (Int, Any?) -> Unit
): Pair<(Int, T?, Map<String, String>?) -> Unit, (Int, Any?) -> Unit> {
    val successCallback: (Int, T?, Map<String, String>?) -> Unit = { code, response, _ ->
        onSuccess(code, response)
    }

    val failureCallback: (Int, Any?) -> Unit = { code, error ->
        onFailure(code, error)
    }

    return Pair(successCallback, failureCallback)
}
