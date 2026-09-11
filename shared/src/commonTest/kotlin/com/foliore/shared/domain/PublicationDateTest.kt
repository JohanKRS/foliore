package com.foliore.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicationDateTest {
    @Test fun keepsKnownPrecisionWithoutInventingMonthOrDay() {
        assertEquals("2016", "2016".publicationDateOrNull())
        assertEquals("2007-03", "March 2007".publicationDateOrNull())
        assertEquals("1978-08-01", "August 1, 1978".publicationDateOrNull())
        assertEquals("2007-03", "2007-03".publicationDateOrNull())
        assertEquals("1999-08-07", "1999-08-07T00:00:00Z".publicationDateOrNull())
    }

    @Test fun invalidOrUnknownDatesCannotProduceAnInvalidEpubDate() {
        assertEquals("2023", "2023-02-29".publicationDateOrNull())
        assertEquals("2024-02-29", "2024-02-29".publicationDateOrNull())
        assertEquals("2015", "DL 2015".publicationDateOrNull())
        assertNull("unknown".publicationDateOrNull())
        assertNull(null.publicationDateOrNull())
    }
}
