package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EbookFormatTest {
    @Test
    fun acceptsProviderMimeTypesAndCaseInsensitiveExtensions() {
        assertEquals(EbookFormat.MOBI, EbookFormat.from("Meu Livro.MoBi", "application/octet-stream"))
        assertEquals(EbookFormat.AZW3, EbookFormat.from("document", "application/vnd.amazon.ebook"))
        assertEquals(EbookFormat.AZW3, EbookFormat.from("document", "application/x-mobi8-ebook"))
        assertEquals(EbookFormat.EPUB, EbookFormat.from("document", "application/x-epub+zip"))
        assertEquals(EbookFormat.EPUB, EbookFormat.from("Book.epub", "application/vnd.amazon.ebook"))
        assertNull(EbookFormat.from("notes.pdf", "application/pdf"))
        assertNull(EbookFormat.from("book.mobi.zip", "application/octet-stream"))
    }
}
