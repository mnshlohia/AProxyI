package com.aproxyi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aproxyi.core.AProxyIConfig
import com.aproxyi.core.RequestStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AProxyITest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun init(config: AProxyIConfig) {
        AProxyI.resetForTesting()
        // Notifications are off: this exercises capture, not the notification UI.
        AProxyI.init(context, config.copy(showNotification = false, logToLogcat = false))
    }

    @Before
    fun setUp() = init(AProxyIConfig.DEBUG)

    @After
    fun tearDown() = AProxyI.resetForTesting()

    // ---------- stats ----------

    @Test
    fun `counts a successful request`() {
        val id = AProxyI.onRequestStart("https://example.com/a")
        assertEquals(1, AProxyI.getStats().active)

        AProxyI.onRequestSuccess(id, 200, "{}")

        val stats = AProxyI.getStats()
        assertEquals(0, stats.active)
        assertEquals(1, stats.successful)
        assertEquals(1, stats.total)
    }

    @Test
    fun `counts a failed request`() {
        val id = AProxyI.onRequestStart("https://example.com/b")
        AProxyI.onRequestFailed(id, 500, IllegalStateException("boom"))

        val stats = AProxyI.getStats()
        assertEquals(0, stats.active)
        assertEquals(1, stats.failed)
    }

    @Test
    fun `a duplicate completion call does not double count`() {
        val id = AProxyI.onRequestStart("https://example.com/c")
        AProxyI.onRequestSuccess(id, 200)
        AProxyI.onRequestSuccess(id, 200)

        assertEquals(1, AProxyI.getStats().successful)
        assertEquals(0, AProxyI.getStats().active)
    }

    @Test
    fun `a disabled config captures nothing`() {
        init(AProxyIConfig.RELEASE)

        assertEquals("", AProxyI.onRequestStart("https://example.com/x"))
        assertEquals(0, AProxyI.getStats().total)
    }

    // ---------- clearAll (defect 5) ----------

    @Test
    fun `clearAll also resets in-flight state`() {
        AProxyI.onRequestStart("https://example.com/inflight")
        assertEquals(1, AProxyI.getStats().active)

        AProxyI.clearAll()

        val stats = AProxyI.getStats()
        // The active count used to survive a Clear and drift upward forever.
        assertEquals(0, stats.active)
        assertEquals(0, stats.total)
        assertTrue(AProxyI.getRequests().isEmpty())
    }

    // ---------- stale sweep (defect 4) ----------

    @Test
    fun `a request with no completion call is swept into TIMED_OUT`() {
        init(AProxyIConfig.DEBUG.copy(activeRequestTimeoutMs = 1L))

        AProxyI.onRequestStart("https://example.com/abandoned")
        assertEquals(1, AProxyI.getStats().active)

        Thread.sleep(20)
        // The sweep runs on the request path.
        AProxyI.onRequestStart("https://example.com/next")

        val timedOut = AProxyI.getRequests(RequestStatus.TIMED_OUT)
        assertEquals(1, timedOut.size)
        assertTrue(timedOut.first().url.contains("abandoned"))
        // Swept requests count as completed, not as perpetually active.
        assertEquals(1, AProxyI.getStats().active)
    }

    @Test
    fun `a zero timeout disables the sweep`() {
        init(AProxyIConfig.DEBUG.copy(activeRequestTimeoutMs = 0L))

        AProxyI.onRequestStart("https://example.com/abandoned")
        Thread.sleep(20)
        AProxyI.onRequestStart("https://example.com/next")

        assertTrue(AProxyI.getRequests(RequestStatus.TIMED_OUT).isEmpty())
        assertEquals(2, AProxyI.getStats().active)
    }

    // ---------- redaction end to end (defects 6 and 7) ----------

    @Test
    fun `credentials are redacted before they reach the store`() {
        val id = AProxyI.onRequestStart(
            url = "https://example.com/v1?access_token=SECRET&page=2",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer SECRET", "Accept" to "application/json"),
            params = mapOf("api_key" to "SECRET", "size" to "10")
        )
        AProxyI.onRequestSuccess(id, 200, "{}", mapOf("Set-Cookie" to "sid=SECRET"))
        assertTrue(AProxyI.awaitIdleForTesting())

        val stored = AProxyI.getRequest(id)!!
        val redacted = AProxyIConfig.REDACTED

        assertTrue(redacted in stored.url)
        assertTrue("SECRET" !in stored.url)
        assertTrue("page=2" in stored.url)

        assertEquals(redacted, stored.headers!!["Authorization"])
        assertEquals("application/json", stored.headers!!["Accept"])
        assertEquals(redacted, stored.params!!["api_key"])
        assertEquals("10", stored.params!!["size"])
        assertEquals(redacted, stored.responseHeaders!!["Set-Cookie"])
    }

    // ---------- retention ----------

    @Test
    fun `the stored list is trimmed to maxRequests`() {
        init(AProxyIConfig.DEBUG.copy(maxRequests = 3))

        repeat(10) { i ->
            val id = AProxyI.onRequestStart("https://example.com/$i")
            AProxyI.onRequestSuccess(id, 200)
        }
        assertTrue(AProxyI.awaitIdleForTesting())

        val stored = AProxyI.getRequests()
        assertEquals(3, stored.size)
        // Newest first: the most recent completions survive.
        assertTrue(stored.first().url.endsWith("/9"))
    }

    @Test
    fun `search matches url method and tag`() {
        val id = AProxyI.onRequestStart("https://example.com/orders", "POST", tag = "checkout")
        AProxyI.onRequestSuccess(id, 200)
        assertTrue(AProxyI.awaitIdleForTesting())

        assertEquals(1, AProxyI.searchRequests("orders").size)
        assertEquals(1, AProxyI.searchRequests("post").size)
        assertEquals(1, AProxyI.searchRequests("checkout").size)
        assertEquals(0, AProxyI.searchRequests("nothing-matches").size)
    }

    // ---------- crash safety (defect 1) ----------

    @Test
    fun `completion calls with unknown or empty ids are ignored, not thrown`() {
        AProxyI.onRequestCancelled("does-not-exist")
        AProxyI.onRequestCancelled("")
        AProxyI.onRequestSuccess("does-not-exist", 200)
        AProxyI.onRequestFailed("", 500)

        assertEquals(0, AProxyI.getStats().total)
    }

    @Test
    fun `cancelling records the request as CANCELLED`() {
        val id = AProxyI.onRequestStart("https://example.com/cancelled")
        AProxyI.onRequestCancelled(id)

        assertEquals(1, AProxyI.getRequests(RequestStatus.CANCELLED).size)
        assertEquals(0, AProxyI.getStats().active)
    }

    // ---------- init (defects 8 and 15) ----------

    @Test
    fun `init is idempotent`() {
        AProxyI.init(context, AProxyIConfig.RELEASE)
        // The first init in setUp wins; the RELEASE config must not take effect.
        assertTrue(AProxyI.isEnabled())
    }

    @Test
    fun `init drives AnalyticsInspector too`() {
        init(AProxyIConfig.RELEASE)
        AnalyticsInspector.clearAll()
        AnalyticsInspector.logEvent("should_not_record", emptyMap<String, Any?>())
        AnalyticsInspector.awaitIdleForTesting()
        // Previously AnalyticsInspector defaulted to enabled and ignored the config.
        assertEquals(0, AnalyticsInspector.getEventCount())

        init(AProxyIConfig.DEBUG)
        AnalyticsInspector.logEvent("should_record", emptyMap<String, Any?>())
        AnalyticsInspector.awaitIdleForTesting()
        assertEquals(1, AnalyticsInspector.getEventCount())
    }

    @Test
    fun `launch intents are resolvable`() {
        assertNotNull(AProxyI.getLaunchIntent(context).component)
        assertNotNull(AProxyI.getAnalyticsLaunchIntent(context).component)
    }
}
