package com.foliore.android.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleBooksCoverUrlNormalizerTest {
    @Test
    fun prefersLargerLinksBeforeThumbnailLinks() {
        val urls = GoogleBooksCoverUrlNormalizer.orderedCoverUrls(
            extraLarge = "http://books.google.com/books/content?id=book&zoom=1&edge=curl",
            large = "http://books.google.com/books/content?id=book&zoom=1",
            medium = null,
            small = null,
            thumbnail = "http://books.google.com/books/content?id=thumb&zoom=1",
            smallThumbnail = null,
        )

        assertEquals(
            "https://books.google.com/books/content?id=book&zoom=0",
            urls.first(),
        )
        assertTrue(urls.indexOf("https://books.google.com/books/content?id=thumb&zoom=0") > 1)
    }

    @Test
    fun removesDecorativeEdgeAndKeepsHttpsCandidatesDistinct() {
        val urls = GoogleBooksCoverUrlNormalizer.candidatesFor(
            "http://books.google.com/books/content?id=book&printsec=frontcover&img=1&zoom=1&edge=curl",
        )

        assertEquals(
            listOf(
                "https://books.google.com/books/content?id=book&printsec=frontcover&img=1&zoom=0",
                "https://books.google.com/books/content?id=book&printsec=frontcover&img=1&zoom=1",
            ),
            urls,
        )
    }
}
