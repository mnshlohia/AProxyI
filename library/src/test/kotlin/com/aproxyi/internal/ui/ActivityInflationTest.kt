package com.aproxyi.internal.ui

import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.aproxyi.AProxyI
import com.aproxyi.AnalyticsInspector
import com.aproxyi.R
import com.aproxyi.core.AProxyIConfig
import com.aproxyi.core.AnalyticsSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Inflates and drives the real activities against the real layouts.
 *
 * This is the closest thing to a device run available without an emulator, and
 * it is what catches the failures a pure-logic test cannot: a layout missing an
 * id the code calls findViewById on, a view declared as the wrong type, or a
 * theme that cannot inflate Material components.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityInflationTest {

    @Before
    fun setUp() {
        AProxyI.resetForTesting()
        AProxyI.init(
            ApplicationProvider.getApplicationContext(),
            AProxyIConfig.DEBUG.copy(showNotification = false, logToLogcat = false)
        )
        // AnalyticsInspector is a separate singleton; without this, events leak
        // between tests.
        AnalyticsInspector.clearAll()
    }

    @After
    fun tearDown() = AProxyI.resetForTesting()

    private fun captureRequest(url: String, code: Int = 200): String {
        val id = AProxyI.onRequestStart(url, "POST", body = """{"a":1}""")
        AProxyI.onRequestSuccess(id, code, """{"ok":true}""")
        AProxyI.awaitIdleForTesting()
        return id
    }

    // ---------- request list ----------

    @Test
    fun `request list inflates and finds every view it uses`() {
        val activity = Robolectric.buildActivity(RequestListActivity::class.java)
            .create().start().resume().get()

        // Each of these is a findViewById or view-binding field the code relies
        // on; a missing or mistyped id in the layout fails here.
        assertNotNull(activity.findViewById<RecyclerView>(R.id.recyclerView))
        assertNotNull(activity.findViewById<TextView>(R.id.tvStats))
        assertNotNull(activity.findViewById<TextView>(R.id.emptyView))
        assertNotNull(activity.findViewById<TextView>(R.id.events))
        assertNotNull(activity.findViewById<AppCompatEditText>(R.id.etSearch))
        assertNotNull(activity.findViewById<android.view.View>(R.id.chipAll))
        assertNotNull(activity.findViewById<android.view.View>(R.id.chipSuccess))
        assertNotNull(activity.findViewById<android.view.View>(R.id.chipFailed))
        assertNotNull(activity.findViewById<android.view.View>(R.id.chipInProgress))
        assertNotNull(activity.findViewById<android.view.View>(R.id.toolbar))
    }

    @Test
    fun `request list shows the empty state with no captures`() {
        val activity = Robolectric.buildActivity(RequestListActivity::class.java)
            .create().start().resume().get()

        val empty = activity.findViewById<TextView>(R.id.emptyView)
        assertEquals(android.view.View.VISIBLE, empty.visibility)
    }

    @Test
    fun `request list binds captured requests into the adapter`() {
        captureRequest("https://example.com/orders")
        captureRequest("https://example.com/profile")

        val activity = Robolectric.buildActivity(RequestListActivity::class.java)
            .create().start().resume().get()

        val list = activity.findViewById<RecyclerView>(R.id.recyclerView)
        assertEquals(2, list.adapter?.itemCount)
        assertEquals(android.view.View.GONE, activity.findViewById<TextView>(R.id.emptyView).visibility)
    }

    @Test
    fun `request list renders the row layout without crashing`() {
        captureRequest("https://example.com/orders")

        val activity = Robolectric.buildActivity(RequestListActivity::class.java)
            .create().start().resume().get()
        val list = activity.findViewById<RecyclerView>(R.id.recyclerView)

        // Force a real bind: this inflates item_request and touches every id the
        // view holder looks up.
        list.measure(0, 0)
        list.layout(0, 0, 1080, 1920)

        assertTrue((list.adapter?.itemCount ?: 0) > 0)
        assertNotNull(list.findViewHolderForAdapterPosition(0))
    }

    @Test
    fun `stats bar reflects captured traffic`() {
        captureRequest("https://example.com/ok", code = 200)

        val activity = Robolectric.buildActivity(RequestListActivity::class.java)
            .create().start().resume().get()

        val stats = activity.findViewById<TextView>(R.id.tvStats).text.toString()
        assertTrue("stats was '$stats'", stats.contains("1 total"))
    }

    // ---------- request detail ----------

    @Test
    fun `request detail inflates and renders a captured request`() {
        val id = captureRequest("https://example.com/orders")

        val intent = RequestDetailActivity.newIntent(
            ApplicationProvider.getApplicationContext(), id
        )
        val activity = Robolectric.buildActivity(RequestDetailActivity::class.java, intent)
            .create().start().resume().get()

        assertEquals("POST", activity.findViewById<TextView>(R.id.tvMethod).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.tvUrl).text.contains("example.com"))
        assertTrue(activity.findViewById<TextView>(R.id.tvContent).text.isNotEmpty())
        assertNotNull(activity.findViewById<android.view.View>(R.id.tabOverview))
        assertNotNull(activity.findViewById<android.view.View>(R.id.tabRequest))
        assertNotNull(activity.findViewById<android.view.View>(R.id.tabResponse))
    }

    @Test
    fun `request detail switches tabs`() {
        val id = captureRequest("https://example.com/orders")
        val intent = RequestDetailActivity.newIntent(
            ApplicationProvider.getApplicationContext(), id
        )
        val activity = Robolectric.buildActivity(RequestDetailActivity::class.java, intent)
            .create().start().resume().get()

        val content = activity.findViewById<TextView>(R.id.tvContent)
        val overview = content.text.toString()

        activity.findViewById<android.view.View>(R.id.tabResponse).performClick()
        val response = content.text.toString()

        assertTrue("tab content did not change", overview != response)
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun `request detail survives an unknown request id`() {
        val intent = RequestDetailActivity.newIntent(
            ApplicationProvider.getApplicationContext(), "does-not-exist"
        )
        // Must not crash: the id can go stale if the list was cleared.
        Robolectric.buildActivity(RequestDetailActivity::class.java, intent)
            .create().start().resume().get()
    }

    // ---------- analytics ----------

    @Test
    fun `analytics list inflates and binds events`() {
        AnalyticsInspector.logEvent("add_to_cart", mapOf("sku" to "1"), AnalyticsSource.FIREBASE)
        AnalyticsInspector.awaitIdleForTesting()

        val activity = Robolectric.buildActivity(AnalyticsListActivity::class.java)
            .create().start().resume().get()

        val list = activity.findViewById<RecyclerView>(R.id.rvEvents)
        assertNotNull(list)
        assertEquals(1, list.adapter?.itemCount)
        assertNotNull(activity.findViewById<TextView>(R.id.tvEventCount))
        assertNotNull(activity.findViewById<TextView>(R.id.tvClear))
        assertNotNull(activity.findViewById<TextView>(R.id.tvEmpty))

        list.measure(0, 0)
        list.layout(0, 0, 1080, 1920)
        assertNotNull(list.findViewHolderForAdapterPosition(0))
    }

    @Test
    fun `analytics detail inflates and renders an event`() {
        AnalyticsInspector.logEvent("purchase", mapOf("value" to "9.99"), AnalyticsSource.CLEVERTAP)
        AnalyticsInspector.awaitIdleForTesting()
        val event = AnalyticsInspector.getEvents().first()

        val intent = AnalyticsDetailActivity.intent(
            ApplicationProvider.getApplicationContext(), event.id
        )
        val activity = Robolectric.buildActivity(AnalyticsDetailActivity::class.java, intent)
            .create().start().resume().get()

        assertEquals("purchase", activity.findViewById<TextView>(R.id.tvEventName).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.tvParams).text.contains("value"))
        assertNotNull(activity.findViewById<android.view.View>(R.id.btnCopy))
    }

    // ---------- redaction reaches the screen ----------

    @Test
    fun `the detail screen never shows a raw credential`() {
        val id = AProxyI.onRequestStart(
            url = "https://example.com/v1?access_token=SUPERSECRET",
            method = "GET",
            headers = mapOf("Authorization" to "Bearer SUPERSECRET")
        )
        AProxyI.onRequestSuccess(id, 200, "{}")
        AProxyI.awaitIdleForTesting()

        val intent = RequestDetailActivity.newIntent(
            ApplicationProvider.getApplicationContext(), id
        )
        val activity = Robolectric.buildActivity(RequestDetailActivity::class.java, intent)
            .create().start().resume().get()

        val onScreen = buildString {
            append(activity.findViewById<TextView>(R.id.tvUrl).text)
            append(activity.findViewById<TextView>(R.id.tvContent).text)
            activity.findViewById<android.view.View>(R.id.tabRequest).performClick()
            append(activity.findViewById<TextView>(R.id.tvContent).text)
        }

        assertTrue("a raw credential reached the screen", "SUPERSECRET" !in onScreen)
        assertTrue(AProxyIConfig.REDACTED in onScreen)
    }
}
