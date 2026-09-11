package com.foliore.android.metadata

import kotlinx.serialization.json.*
import kotlin.test.*

class OpenCatalogSourcesTest {
    @Test fun archiveHandlesScalarAndListFieldsWithoutUsingScanDimensionsAsSynopsis() {
        val candidate = internetArchiveCandidate(Json.parseToJsonElement("""{
            "identifier":"deathsend0000liuc", "title":"Death's end", "creator":"Liu, Cixin, author",
            "isbn":["9780765377104","0765377101"], "language":"eng", "publisher":"Tor", "date":"2016-01-01T00:00:00Z",
            "description":["604 pages ; 25 cm","A sufficiently detailed description of this science fiction novel and its imagined future world."],
            "subject":["Science fiction","Alien encounters"]} """).jsonObject)!!
        assertEquals(listOf("Cixin Liu"), candidate.metadata.authors)
        assertEquals("Liu, Cixin", candidate.metadata.sortAuthor)
        assertEquals("2016", candidate.metadata.publishedDate)
        assertEquals("en", candidate.metadata.language)
        assertFalse(candidate.metadata.description!!.contains("604 pages"))
        assertEquals(setOf("9780765377104"), candidate.matchedIsbns)
    }
    @Test fun archiveUsesIsbnThenTitleAndPreservesOtherProviderSuccessOnFailure() {
        val urls = mutableListOf<String>()
        val result = searchInternetArchive(emptySourceMetadata("Dune").copy(isbn = "9780441172719", authors = listOf("Frank Herbert")), 12) {
            urls += it
            if (urls.size == 1) """{"response":{"docs":[]}}"""
            else """{"response":{"docs":[{"identifier":"dune01","title":"Dune","creator":["Herbert, Frank"],"language":"eng"}]}}"""
        }
        assertEquals(2, urls.size)
        assertEquals("Dune", assertIs<MetadataLookupResult.Success>(result).candidates.single().metadata.title)
        assertIs<MetadataLookupResult.Failure>(searchInternetArchive(emptySourceMetadata("Dune"), 12) { throw java.io.IOException() })
    }
    @Test fun bnfReadsEditionFieldsAndDoesNotPromoteTranslatorsToAuthors() {
        val xml = """<response xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <oai_dc:dc><dc:identifier>http://catalogue.bnf.fr/ark:/12148/cb12345x</dc:identifier>
          <dc:identifier>ISBN 9782070612758</dc:identifier><dc:title>Le petit prince / Antoine de Saint-Exupéry</dc:title>
          <dc:creator>Saint-Exupéry, Antoine de (1900-1944). Auteur du texte</dc:creator>
          <dc:contributor>Someone Else. Traducteur</dc:contributor><dc:publisher>Gallimard</dc:publisher>
          <dc:date>2007</dc:date><dc:language>fre</dc:language><dc:description>Collection : Folio</dc:description>
          <dc:description>Code à barres commercial : EAN 9782070612758</dc:description></oai_dc:dc></response>"""
        val m = parseBnfRecords(xml).single().metadata
        assertEquals("Le petit prince", m.title)
        assertEquals(listOf("Antoine de Saint-Exupéry"), m.authors)
        assertEquals("fr", m.language)
        assertEquals("Folio", m.seriesName)
        assertNull(m.description)
    }
    @Test fun bnfRejectsDoctypesAndEntityExpansion() {
        assertFails { parseBnfRecords("""<!DOCTYPE a [<!ENTITY x SYSTEM "file:///etc/passwd">]><a>&x;</a>""") }
        assertFails { parseBnfRecords("""<r xmlns:d="http://www.loc.gov/zing/srw/diagnostic/"><d:diagnostic/></r>""") }
    }
    @Test fun catalogMarkdownIsReadableAndDoesNotImportDownloadPromotions() {
        val text = "*A novel* with **bold text**. [Wikipedia](https://en.wikipedia.org/wiki/Example) [**PDF**](https://example.invalid/download)"
        assertEquals("A novel with bold text. Wikipedia", text.displayMetadataText())
    }
    @Test fun missingGoogleFieldsAreNotMisrepresentedAsFoundData() {
        val current = emptySourceMetadata("Dune").copy(description = "Local text", publisher = "Local publisher")
        val candidate = googleCandidate(Json.parseToJsonElement("""{"id":"dune","volumeInfo":{"title":"Dune"}}""").jsonObject, current)!!
        assertNull(candidate.metadata.description)
        assertNull(candidate.metadata.publisher)
        assertTrue(candidate.metadata.authors.isEmpty())
    }
}
