package com.networkinspector.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json is stubbed on the JVM, so this needs Robolectric's real implementation.
@RunWith(RobolectricTestRunner::class)
class BodyFormatterTest {

    @Test
    fun `pretty-prints small json`() {
        val out = BodyFormatter.format("""{"a":1,"b":{"c":2}}""")!!
        assertTrue(out.contains("\n"))
        assertTrue(out.contains("\"a\""))
    }

    @Test
    fun `leaves a large body raw instead of parsing it`() {
        // tryFormatJson parses and re-serialises the WHOLE payload before
        // truncating, so maxSize never bounded the work. Past PRETTY_PRINT_LIMIT
        // (64KB) the body must be stored raw.
        val big = "{\"k\":\"" + "x".repeat(80_000) + "\"}"

        val started = System.nanoTime()
        val out = BodyFormatter.format(big, maxSize = 200_000)!!
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("should not be pretty-printed", !out.startsWith("{\n"))
        assertTrue("should stay cheap, took ${elapsedMs}ms", elapsedMs < 1_000)
    }

    @Test
    fun `truncates to maxSize`() {
        val out = BodyFormatter.format("y".repeat(5_000), maxSize = 100)!!
        assertEquals(100, out.length)
    }

    @Test
    fun `returns null for a null body`() {
        assertEquals(null, BodyFormatter.format(null))
    }

    @Test
    fun `falls back rather than throwing on a hostile object`() {
        val hostile = object {
            override fun toString(): String = throw IllegalStateException("nope")
        }
        // Must degrade, never propagate: this runs on the request path.
        assertTrue(BodyFormatter.format(hostile)!!.isNotEmpty())
    }
}
