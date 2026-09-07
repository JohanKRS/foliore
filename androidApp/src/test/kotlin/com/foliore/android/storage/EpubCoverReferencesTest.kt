package com.foliore.android.storage

import kotlin.test.*

class EpubCoverReferencesTest {
    @Test fun jpegReplacementUpdatesManifestAndRelativeReferences() {
        for (type in listOf("image/png", "image/svg+xml", "image/jpeg")) {
            val opf = """<manifest><item id="cover" href="images/old.png" media-type="$type" properties="cover-image"/></manifest>"""
            val actual = EpubCoverReferences.rewrite(opf, "OPS/content.opf", "OPS/images/old.png", "OPS/foliore.jpg")
            assertTrue(actual.contains("href=\"foliore.jpg\""))
            assertTrue(actual.contains("media-type=\"image/jpeg\""))
            assertTrue(actual.contains("properties=\"cover-image\""))
        }
        val page = """<img src='../images/old.png'/><image xlink:href="../images/old.png"/><a href="https://example.com/old.png"/>"""
        val result = EpubCoverReferences.rewrite(page, "OPS/text/cover.xhtml", "OPS/images/old.png", "OPS/foliore.jpg")
        assertTrue(result.contains("src='../foliore.jpg'"))
        assertTrue(result.contains("xlink:href=\"../foliore.jpg\""))
        assertTrue(result.contains("https://example.com/old.png"))
        assertEquals("background:url(\"../foliore.jpg\")", EpubCoverReferences.rewrite("background:url('../images/old.png')", "OPS/css/main.css", "OPS/images/old.png", "OPS/foliore.jpg"))
    }
}
