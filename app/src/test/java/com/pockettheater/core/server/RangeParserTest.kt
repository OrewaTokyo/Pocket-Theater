package com.pockettheater.core.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeParserTest {
    @Test
    fun noHeaderMeansTheWholeFile() {
        val span = RangeParser.parse(null, 100)!!
        assertEquals(0L, span.start)
        assertEquals(99L, span.end)
        assertFalse(span.partial)
    }

    @Test
    fun aNormalRange() {
        val span = RangeParser.parse("bytes=10-49", 100)!!
        assertEquals(10L, span.start)
        assertEquals(49L, span.end)
        assertTrue(span.partial)
    }

    @Test
    fun anOpenEndedRangeRunsToTheEnd() {
        val span = RangeParser.parse("bytes=50-", 100)!!
        assertEquals(50L, span.start)
        assertEquals(99L, span.end)
    }

    @Test
    fun aSuffixRangeIsTheLastBytes() {
        val span = RangeParser.parse("bytes=-10", 100)!!
        assertEquals(90L, span.start)
        assertEquals(99L, span.end)
    }

    @Test
    fun anEndBeyondTheFileIsCutToTheFile() {
        val span = RangeParser.parse("bytes=90-500", 100)!!
        assertEquals(99L, span.end)
    }

    @Test
    fun rangesThatCannotBeServedAreRefused() {
        assertNull(RangeParser.parse("bytes=100-", 100))
        assertNull(RangeParser.parse("bytes=5-3", 100))
        assertNull(RangeParser.parse("bytes=0-10", 0))
    }

    @Test
    fun headersItDoesNotUnderstandAreIgnored() {
        assertFalse(RangeParser.parse("bytes=abc-", 100)!!.partial)
        assertFalse(RangeParser.parse("items=1-2", 100)!!.partial)
        assertNotNull(RangeParser.parse("garbage", 100))
    }
}
