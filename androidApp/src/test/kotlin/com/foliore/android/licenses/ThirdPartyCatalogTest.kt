package com.foliore.android.licenses

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdPartyCatalogTest {
    private val assets = File("src/main/assets")
    private val catalog = ThirdPartyCatalog.parse(File(assets, "licenses/catalog.json").readText())

    @Test
    fun everyNoticeIsReadableOfflineAndPreservesItsReviewedBytes() {
        assertEquals(catalog.groups.size, catalog.groups.map { it.id }.toSet().size)
        val coordinates = catalog.groups.flatMap { it.coordinates }
        assertEquals(coordinates.size, coordinates.toSet().size)
        catalog.groups.forEach { group ->
            assertTrue(group.notices.isNotEmpty(), group.name)
            group.notices.forEach { notice ->
                val file = File(assets, notice.path).canonicalFile
                assertTrue(file.toPath().startsWith(assets.canonicalFile.toPath()), notice.path)
                assertTrue(file.isFile, notice.path)
                val bytes = file.readBytes()
                assertTrue(bytes.size > 100, notice.title)
                assertEquals(notice.sha256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                assertTrue(notice.source.isNotBlank(), notice.title)
            }
        }
    }

    @Test
    fun bundledGoogleNoticesFontAndPublicSuffixRightsAreExposed() {
        val google = catalog.groups.single { it.id == "google-play" }
        assertTrue(google.notices.any { it.title == "Animal Sniffer" })
        assertTrue(google.notices.any { it.title == "Protocol Buffers" })
        val font = catalog.groups.single { it.id == "cormorant" }.notices.single()
        assertTrue(File(assets, font.path).readText().contains("Copyright 2015 the Cormorant Project Authors"))
        val http = catalog.groups.single { it.id == "okhttp" }
        assertTrue(http.notices.any { it.title == "Mozilla Public License 2.0" })
        assertTrue(http.notices.any { it.title == "Public Suffix List" })
    }
}
