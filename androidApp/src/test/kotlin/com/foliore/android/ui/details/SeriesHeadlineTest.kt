package com.foliore.android.ui.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesHeadlineTest {
    @Test
    fun noSeriesShowsNothing() {
        assertNull(seriesHeadline("", "2"))
        assertNull(seriesHeadline("   ", ""))
    }

    @Test
    fun seriesWithoutNumberShowsName() {
        assertEquals("Space Odyssey", seriesHeadline(" Space Odyssey ", " "))
    }

    @Test
    fun seriesWithNumberMatchesLibraryForm() {
        assertEquals("Space Odyssey #2", seriesHeadline("Space Odyssey", "2"))
        assertEquals("Discworld #2.5", seriesHeadline("Discworld", "2.5"))
    }
}
