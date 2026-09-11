package com.networkinspector.core

/**
 * Configuration options for NetworkInspector.
 *
 * Lives in `:library-api` so that `:library` and `:library-no-op` share one
 * definition and cannot drift apart.
 */
data class NetworkInspectorConfig(
    /** Whether the inspector is enabled */
    val enabled: Boolean = true,

    /** Whether to show notifications */
    val showNotification: Boolean = true,

    /** Maximum number of requests to store in memory */
    val maxRequests: Int = 500,

    /** Maximum size for request/response bodies (in characters) */
    val maxBodySize: Int = 200_000,

    /** Whether to log to Logcat */
    val logToLogcat: Boolean = true,

    /** Custom notification channel name */
    val notificationChannelName: String = "Network Inspector",

    /**
     * Regex patterns matched against the URL's **host only** (e.g. `analytics\\.`).
     * A match excludes the request from tracking.
     */
    val excludedHosts: List<String> = emptyList(),

    /**
     * Regex patterns matched against the URL's **path only**, excluding the
     * query string (e.g. `^/health$`). A match excludes the request.
     */
    val excludedPaths: List<String> = emptyList(),

    /**
     * Header names whose values are replaced with [REDACTED] at capture time.
     * Matched case-insensitively.
     */
    val redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,

    /**
     * Query parameter names whose values are replaced with [REDACTED] at
     * capture time. Matched case-insensitively, and additionally by the
     * substring rules in [SENSITIVE_QUERY_FRAGMENTS].
     */
    val redactedQueryParams: Set<String> = DEFAULT_REDACTED_QUERY_PARAMS,

    /**
     * An in-flight request with no completion call after this long is swept
     * into [RequestStatus.TIMED_OUT]. Without this, a missed
     * `onRequestSuccess` / `onRequestFailed` leaks its entry and permanently
     * inflates the active count. Set to 0 to disable the sweep.
     */
    val activeRequestTimeoutMs: Long = 60_000L
) {
    companion object {
        /** Replacement written in place of a sensitive value. */
        const val REDACTED = "**REDACTED**"

        val DEFAULT_REDACTED_HEADERS: Set<String> = setOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "Set-Cookie",
            "X-Api-Key",
            "X-Auth-Token",
            "X-Access-Token",
            "X-Csrf-Token"
        )

        val DEFAULT_REDACTED_QUERY_PARAMS: Set<String> = setOf(
            "key", "sid", "sig", "otp", "pin"
        )

        /**
         * A query parameter is also redacted when its name *contains* any of
         * these, which catches `access_token`, `oauth_token`, `api_key`,
         * `client_secret` and friends without enumerating every vendor spelling.
         */
        val SENSITIVE_QUERY_FRAGMENTS: List<String> = listOf(
            "token", "secret", "password", "passwd", "pwd", "auth",
            "signature", "session", "apikey", "api_key", "credential"
        )

        /** Default configuration for debug builds */
        val DEBUG = NetworkInspectorConfig(
            enabled = true,
            showNotification = true,
            logToLogcat = true
        )

        /** Configuration for release builds (disabled) */
        val RELEASE = NetworkInspectorConfig(
            enabled = false,
            showNotification = false,
            logToLogcat = false
        )
    }

    // Compiled once per config rather than once per request. An invalid pattern
    // is dropped rather than thrown, so a bad exclusion cannot break capture.
    private val excludedHostRegexes: List<Regex> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        excludedHosts.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
    }

    private val excludedPathRegexes: List<Regex> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        excludedPaths.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
    }

    private val redactedHeadersLower: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        redactedHeaders.mapTo(HashSet()) { it.lowercase() }
    }

    private val redactedQueryParamsLower: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        redactedQueryParams.mapTo(HashSet()) { it.lowercase() }
    }

    /**
     * Check if a URL should be tracked based on exclusion rules
     */
    fun shouldTrack(url: String): Boolean {
        if (!enabled) return false

        if (excludedHostRegexes.isNotEmpty()) {
            val host = hostOf(url)
            if (excludedHostRegexes.any { it.containsMatchIn(host) }) return false
        }

        if (excludedPathRegexes.isNotEmpty()) {
            val path = pathOf(url)
            if (excludedPathRegexes.any { it.containsMatchIn(path) }) return false
        }

        return true
    }

    /**
     * Host portion of [url], without scheme, path, query or credentials.
     *
     * Hand-parsed rather than via java.net.URI so that a malformed URL degrades
     * to a best-effort string instead of throwing on the request path.
     */
    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.substringAfterLast('@').substringBefore(':')
    }

    /** Path portion of [url], without host, query or fragment. */
    private fun pathOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return "/"
        return afterScheme.substring(slash).substringBefore('?').substringBefore('#')
    }

    /** True when a header's value must never be stored. */
    fun isSensitiveHeader(name: String): Boolean =
        name.lowercase() in redactedHeadersLower

    /** True when a query parameter's value must never be stored. */
    fun isSensitiveQueryParam(name: String): Boolean {
        val lower = name.lowercase()
        if (lower in redactedQueryParamsLower) return true
        return SENSITIVE_QUERY_FRAGMENTS.any { lower.contains(it) }
    }

    /**
     * Replace sensitive header values with [REDACTED].
     *
     * Applied at capture time: secrets never enter the in-memory store, so they
     * cannot escape through the UI, share, or copy-as-cURL.
     */
    fun redactHeaders(headers: Map<String, String>?): Map<String, String>? {
        if (headers.isNullOrEmpty()) return headers
        return headers.mapValues { (name, value) ->
            if (isSensitiveHeader(name)) REDACTED else value
        }
    }

    /** Replace sensitive query-parameter values with [REDACTED]. */
    fun redactParams(params: Map<String, String>?): Map<String, String>? {
        if (params.isNullOrEmpty()) return params
        return params.mapValues { (name, value) ->
            if (isSensitiveQueryParam(name)) REDACTED else value
        }
    }

    /**
     * Rewrite a URL so sensitive query-parameter values are replaced with
     * [REDACTED], preserving everything else including any fragment.
     */
    fun redactUrl(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url

        val base = url.substring(0, queryStart)
        val fragmentStart = url.indexOf('#', queryStart)
        val query = if (fragmentStart >= 0) {
            url.substring(queryStart + 1, fragmentStart)
        } else {
            url.substring(queryStart + 1)
        }
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""

        if (query.isEmpty()) return url

        val redactedQuery = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            // No '=' at all, or a leading '=': nothing that looks like a named
            // value, so leave it untouched.
            if (eq <= 0) return@joinToString pair

            val name = pair.substring(0, eq)
            if (isSensitiveQueryParam(name)) "$name=$REDACTED" else pair
        }

        return "$base?$redactedQuery$fragment"
    }
}
