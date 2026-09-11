package com.networkinspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.networkinspector.core.NetworkInspectorConfig
import com.networkinspector.core.RequestStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkInspectorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun init(config: NetworkInspectorConfig) {
        NetworkInspector.resetForTesting()
        // Notifications are off: this exercises capture, not the notification UI.
        NetworkInspector.init(context, config.copy(showNotification = false, logToLogcat = false))
    }

    @Before
    fun setUp() = init(NetworkInspectorConfig.DEBUG)

    @After
    fun tearDown() = NetworkInspector.resetForTesting()

    // ---------- stats ----------

    @Test
    fun `counts a successful request`() {
        val id = NetworkInspector.onRequestStart("https://example.com/a")
        assertEquals(1, NetworkInspector.getStats().active)

        NetworkInspector.onRequestSuccess(id, 200, "{}")

        val stats = NetworkInspector.getStats()
        assertEquals(0, stats.active)
        assertEquals(1, stats.successful)
        assertEquals(1, stats.total)
    }

    @Test
    fun `counts a failed request`() {
        val id = NetworkInspector.onRequestStart("https://example.com/b")
        NetworkInspector.onRequestFailed(id, 500, IllegalStateException("boom"))

        val stats = NetworkInspector.getStats()
        assertEquals(0, stats.active)
        assertEquals(1, stats.failed)
    }

    @Test
    fun `a duplicate completion call does not double count`() {
        val id = NetworkInspector.onRequestStart("https://example.com/c")
        NetworkInspector.onRequestSuccess(id, 200)
        NetworkInspector.onRequestSuccess(id, 200)

        assertEquals(1, NetworkInspector.getStats().successful)
        assertEquals(0, NetworkInspector.getStats().active)
    }

    @Test
    fun `a disabled config captures nothing`() {
        init(NetworkInspectorConfig.RELEASE)

        assertEquals("", NetworkInspector.onRequestStart("https://example.com/x"))
        assertEquals(0, NetworkInspector.getStats().total)
    }

    // ---------- clearAll (defect 5) ----------

    @Test
    fun `clearAll also resets in-flight state`() {
        NetworkInspector.onRequestStart("https://example.com/inflight")
        assertEquals(1, NetworkInspector.getStats().active)

        NetworkInspector.clearAll()

        val stats = NetworkInspector.getStats()
        // The active count used to survive a Clear and drift upward forever.
        assertEquals(0, stats.active)
        assertEquals(0, stats.total)
        assertTrue(NetworkInspector.getRequests().isEmpty())
    }

    // ---------- stale sweep (defect 4) ----------

    @Test
    fun `a request with no completion call is swept into TIMED_OUT`() {
        init(NetworkInspectorConfig.DEBUG.copy(activeRequestTimeoutMs = 1L))

        NetworkInspector.onRequestStart("https://example.com/abandoned")
        assertEquals(1, NetworkInspector.getStats().active)

        Thread.sleep(20)
        // The sweep runs on the request path.
        NetworkInspector.onRequestStart("https://example.com/next")

        val timedOut = NetworkInspector.getRequests(RequestStatus.TIMED_OUT)
        assertEquals(1, timedOut.size)
        assertTrue(timedOut.first().url.contains("abandoned"))
        // Swept requests count as completed, not as perpetually active.
        assertEquals(1, NetworkInspector.getStats().active)
    }

    @Test
    fun `a zero timeout disables the sweep`() {
        init(NetworkInspectorConfig.DEBUG.copy(activeRequestTimeoutMs = 0L))

        NetworkInspector.onRequestStart("https://example.com/abandoned")
        Thread.sleep(20)
        NetworkInspector.onRequestStart("https://example.com/next")

        assertTrue(NetworkInspector.getRequests(RequestStatus.TIMED_OUT).isEmpty())
        assertEquals(2, NetworkInspector.getStats().active)
    }

    // ---------- redaction end to end (defects 6 and 7) ----------

    @Test
    fun `credentials are redacted before they reach the store`() {
        val id = NetworkInspector.onRequestStart(
            url = "https://example.com/v1?access_token=SECRET&page=2",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer SECRET", "Accept" to "application/json"),
            params = mapOf("api_key" to "SECRET", "size" to "10")
        )
        NetworkInspector.onRequestSuccess(id, 200, "{}", mapOf("Set-Cookie" to "sid=SECRET"))
        assertTrue(NetworkInspector.awaitIdleForTesting())

        val stored = NetworkInspector.getRequest(id)!!
        val redacted = NetworkInspectorConfig.REDACTED

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
        init(NetworkInspectorConfig.DEBUG.copy(maxRequests = 3))

        repeat(10) { i ->
            val id = NetworkInspector.onRequestStart("https://example.com/$i")
            NetworkInspector.onRequestSuccess(id, 200)
        }
        assertTrue(NetworkInspector.awaitIdleForTesting())

        val stored = NetworkInspector.getRequests()
        assertEquals(3, stored.size)
        // Newest first: the most recent completions survive.
        assertTrue(stored.first().url.endsWith("/9"))
    }

    @Test
    fun `search matches url method and tag`() {
        val id = NetworkInspector.onRequestStart("https://example.com/orders", "POST", tag = "checkout")
        NetworkInspector.onRequestSuccess(id, 200)
        assertTrue(NetworkInspector.awaitIdleForTesting())

        assertEquals(1, NetworkInspector.searchRequests("orders").size)
        assertEquals(1, NetworkInspector.searchRequests("post").size)
        assertEquals(1, NetworkInspector.searchRequests("checkout").size)
        assertEquals(0, NetworkInspector.searchRequests("nothing-matches").size)
    }

    // ---------- crash safety (defect 1) ----------

    @Test
    fun `completion calls with unknown or empty ids are ignored, not thrown`() {
        NetworkInspector.onRequestCancelled("does-not-exist")
        NetworkInspector.onRequestCancelled("")
        NetworkInspector.onRequestSuccess("does-not-exist", 200)
        NetworkInspector.onRequestFailed("", 500)

        assertEquals(0, NetworkInspector.getStats().total)
    }

    @Test
    fun `cancelling records the request as CANCELLED`() {
        val id = NetworkInspector.onRequestStart("https://example.com/cancelled")
        NetworkInspector.onRequestCancelled(id)

        assertEquals(1, NetworkInspector.getRequests(RequestStatus.CANCELLED).size)
        assertEquals(0, NetworkInspector.getStats().active)
    }

    // ---------- init (defects 8 and 15) ----------

    @Test
    fun `init is idempotent`() {
        NetworkInspector.init(context, NetworkInspectorConfig.RELEASE)
        // The first init in setUp wins; the RELEASE config must not take effect.
        assertTrue(NetworkInspector.isEnabled())
    }

    @Test
    fun `init drives AnalyticsInspector too`() {
        init(NetworkInspectorConfig.RELEASE)
        AnalyticsInspector.clearAll()
        AnalyticsInspector.logEvent("should_not_record", emptyMap<String, Any?>())
        AnalyticsInspector.awaitIdleForTesting()
        // Previously AnalyticsInspector defaulted to enabled and ignored the config.
        assertEquals(0, AnalyticsInspector.getEventCount())

        init(NetworkInspectorConfig.DEBUG)
        AnalyticsInspector.logEvent("should_record", emptyMap<String, Any?>())
        AnalyticsInspector.awaitIdleForTesting()
        assertEquals(1, AnalyticsInspector.getEventCount())
    }

    @Test
    fun `launch intents are resolvable`() {
        assertNotNull(NetworkInspector.getLaunchIntent(context).component)
        assertNotNull(NetworkInspector.getAnalyticsLaunchIntent(context).component)
    }
}
