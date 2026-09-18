package com.foliore.android.reader

import kotlin.test.*

class ReadingStateTest {
    private val hash = "a".repeat(64)
    private fun position(clock: Long, progress: Double, device: String = "a") = ReadingPosition("book", hash, ReadingLocator("OPS/chapter.xhtml", progress, 10, "word"), clock, device)
    @Test fun newerRewindWinsAndMergeIsOrderIndependentIdempotent() {
        val a = ReadingState(listOf(position(10, .8)))
        val b = ReadingState(listOf(position(11, .2, "b")))
        assertEquals(b, a.merge(b)); assertEquals(b, b.merge(a)); assertEquals(b, b.merge(b))
    }
    @Test fun concurrentUpdatesConvergeWithDeterministicTieBreak() {
        val a = ReadingState(listOf(position(10, .8)))
        val b = ReadingState(listOf(position(10, .2, "b")))
        assertEquals(a.merge(b), b.merge(a))
        assertEquals("b", a.merge(b).positions.single().deviceId)
    }
    @Test fun bookmarksMergeIndependentlyAndDeletedOnesDoNotReturn() {
        val marker = ReadingBookmark("mark", "book", hash, position(1,.3).locator, "Chapter", 1, "a")
        val first = ReadingState(listOf(position(20,.7)), listOf(marker))
        val deleted = ReadingState(bookmarks = listOf(marker.copy(deleted = true, clock = 2, deviceId = "b")))
        val merged = first.merge(deleted)
        assertEquals(.7, merged.positions.single().locator.progression)
        assertTrue(merged.bookmarks.single().deleted)
        assertEquals(merged, merged.merge(first))
    }
    @Test fun invalidLocatorsNeverEnterThePortableState() {
        val original = position(1,.3)
        for (loc in listOf(original.locator.copy(href = "../private"), original.locator.copy(href = "https://example.com"),
            original.locator.copy(progression = Double.NaN), original.locator.copy(textOffset = -1))) {
            assertTrue(ReadingState().merge(ReadingState(listOf(original.copy(locator = loc)))).positions.isEmpty())
        }
    }
    @Test fun clockAdvancesBeyondRemoteAndFilteringDropsDeletedBooks() {
        val state = ReadingState(listOf(position(1000,.5)))
        assertEquals(1001, state.nextClock(1)); assertEquals(ReadingState(), state.forBooks(emptySet()))
    }
    @Test fun malformedAppearanceValuesAreBounded() {
        val prefs = ReaderPreferences(fontSize = 400, margin = -20, theme = "unknown", lineHeight = Double.NaN, brightness = Float.NaN).normalized()
        assertEquals(36,prefs.fontSize); assertEquals(8,prefs.margin); assertEquals("paper",prefs.theme); assertEquals(-1f,prefs.brightness)
    }
}
