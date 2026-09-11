package com.aproxyi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redaction is the library's most safety-critical logic: a miss here puts live
 * credentials into an on-device UI that can be screenshotted and shared.
 */
class AProxyIConfigTest {

    private val config = AProxyIConfig()
    private val redacted = AProxyIConfig.REDACTED

    // ---------- headers ----------

    @Test
    fun `redacts credential headers regardless of case`() {
        val result = config.redactHeaders(
            mapOf(
                "Authorization" to "Bearer secret",
                "cookie" to "session=abc",
                "X-API-KEY" to "key123",
                "Content-Type" to "application/json"
            )
        )!!

        assertEquals(redacted, result["Authorization"])
        assertEquals(redacted, result["cookie"])
        assertEquals(redacted, result["X-API-KEY"])
        // Non-sensitive headers must survive, or the tool stops being useful.
        assertEquals("application/json", result["Content-Type"])
    }

    @Test
    fun `passes null and empty headers through untouched`() {
        assertEquals(null, config.redactHeaders(null))
        assertEquals(emptyMap<String, String>(), config.redactHeaders(emptyMap()))
    }

    @Test
    fun `honours a custom redacted header set`() {
        val custom = AProxyIConfig(redactedHeaders = setOf("X-Internal-Sig"))
        val result = custom.redactHeaders(mapOf("X-Internal-Sig" to "sig", "Authorization" to "tok"))!!

        assertEquals(redacted, result["X-Internal-Sig"])
        // Replacing the set replaces it; it does not merge with the defaults.
        assertEquals("tok", result["Authorization"])
    }

    // ---------- query parameters ----------

    @Test
    fun `redacts sensitive query values and keeps the rest`() {
        val result = config.redactUrl(
            "https://api.example.com/v1/orders?access_token=SECRET&page=2&client_secret=S2"
        )

        assertEquals(
            "https://api.example.com/v1/orders?access_token=$redacted&page=2&client_secret=$redacted",
            result
        )
    }

    @Test
    fun `matches sensitive parameter names by substring and by exact name`() {
        assertTrue(config.isSensitiveQueryParam("oauth_token"))
        assertTrue(config.isSensitiveQueryParam("X-Session-Id"))
        assertTrue(config.isSensitiveQueryParam("apikey"))
        assertTrue(config.isSensitiveQueryParam("key"))
        assertTrue(config.isSensitiveQueryParam("sid"))

        // "keyword" contains "key" but is not the exact name, and "key" is an
        // exact-match rule rather than a substring one, so it must not match.
        assertFalse(config.isSensitiveQueryParam("keyword"))
        assertFalse(config.isSensitiveQueryParam("page"))
    }

    @Test
    fun `leaves a url without a query string unchanged`() {
        val url = "https://api.example.com/v1/orders"
        assertEquals(url, config.redactUrl(url))
    }

    @Test
    fun `preserves the fragment when redacting`() {
        assertEquals(
            "https://example.com/p?token=$redacted#section",
            config.redactUrl("https://example.com/p?token=abc#section")
        )
    }

    @Test
    fun `leaves malformed query segments alone`() {
        // No '=' at all, and a leading '=', are not name/value pairs.
        assertEquals(
            "https://example.com/p?flag&=orphan&token=$redacted",
            config.redactUrl("https://example.com/p?flag&=orphan&token=abc")
        )
    }

    @Test
    fun `redacts the separately supplied params map`() {
        val result = config.redactParams(mapOf("api_key" to "SECRET", "page" to "1"))!!
        assertEquals(redacted, result["api_key"])
        assertEquals("1", result["page"])
    }

    // ---------- host / path scoping (defect 13) ----------

    @Test
    fun `excludedHosts matches the host and not the path`() {
        val config = AProxyIConfig(excludedHosts = listOf("analytics"))

        assertFalse(config.shouldTrack("https://analytics.example.com/v1/x"))
        // The old implementation matched the whole URL, so this was wrongly excluded.
        assertTrue(config.shouldTrack("https://api.example.com/analytics/report"))
    }

    @Test
    fun `excludedPaths matches the path and not the host`() {
        val config = AProxyIConfig(excludedPaths = listOf("^/health$"))

        assertFalse(config.shouldTrack("https://api.example.com/health"))
        assertTrue(config.shouldTrack("https://health.example.com/v1/orders"))
        // Anchored pattern must not match a longer path.
        assertTrue(config.shouldTrack("https://api.example.com/health/deep"))
    }

    @Test
    fun `path matching ignores the query string`() {
        val config = AProxyIConfig(excludedPaths = listOf("secret"))
        // "secret" appears only in the query, not the path.
        assertTrue(config.shouldTrack("https://api.example.com/v1/orders?secret=1"))
    }

    @Test
    fun `host matching ignores port and userinfo`() {
        val config = AProxyIConfig(excludedHosts = listOf("^example\\.com$"))
        assertFalse(config.shouldTrack("https://user:pass@example.com:8443/path"))
    }

    @Test
    fun `an invalid exclusion pattern is dropped rather than breaking capture`() {
        val config = AProxyIConfig(excludedHosts = listOf("[unclosed"))
        assertTrue(config.shouldTrack("https://example.com/x"))
    }

    @Test
    fun `a disabled config tracks nothing`() {
        assertFalse(AProxyIConfig.RELEASE.shouldTrack("https://example.com"))
        assertTrue(AProxyIConfig.DEBUG.shouldTrack("https://example.com"))
    }
}
