package com.paperwise.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextMatcherTest {
    @Test
    fun `findMatches returns overlapping matches`() {
        val matches = SearchTextMatcher.findMatches(
            pageText = "banana",
            query = "ana",
            caseSensitive = true
        )

        assertEquals(2, matches.size)
        assertEquals(1, matches[0].startIndex)
        assertEquals(4, matches[0].endIndex)
        assertEquals(3, matches[1].startIndex)
        assertEquals(6, matches[1].endIndex)
    }

    @Test
    fun `findMatches supports case insensitive search`() {
        val matches = SearchTextMatcher.findMatches(
            pageText = "PaperWISE paperwise",
            query = "paperwise",
            caseSensitive = false
        )

        assertEquals(2, matches.size)
        assertEquals("PaperWISE", matches[0].matchText)
        assertEquals("paperwise", matches[1].matchText)
    }

    @Test
    fun `findMatches returns empty for empty inputs`() {
        assertTrue(SearchTextMatcher.findMatches("", "a", caseSensitive = true).isEmpty())
        assertTrue(SearchTextMatcher.findMatches("abc", "", caseSensitive = true).isEmpty())
    }
}
