package com.foliore.android.metadata

import java.util.Locale
import kotlin.test.*

class MetadataSelectionTest {
    private val current = emptySourceMetadata("My title").copy(authors = listOf("My author"), subtitle = "My subtitle",
        language = "pt-BR", publisher = "My publisher", description = "My description", isbn = "9781466853454",
        publishedDate = "2016-09-20", seriesName = "My series", seriesIndex = 3.0, identifiers = mapOf("local" to "keep"))

    @Test fun defaultSelectsAllMetadataButNeverTheCover() {
        assertEquals(MetadataField.entries.toSet() - MetadataField.COVER, MetadataField.defaultSelection)
    }
    @Test fun uncheckedFieldsAndAbsentValuesPreserveTheCurrentDraft() {
        val incoming = emptySourceMetadata("Another title").copy(description = "New description", identifiers = mapOf("archive" to "remote"))
        val result = current.applySelectedMetadata(incoming, MetadataField.defaultSelection - MetadataField.TITLE - MetadataField.IDENTIFIERS)
        assertEquals(current.copy(description = "New description"), result)
        assertEquals(current, current.applySelectedMetadata(incoming, emptySet()))
    }
    @Test fun selectedIdentifiersMergeAndDatesKeepTheirFullPrecision() {
        val result = current.applySelectedMetadata(emptySourceMetadata("Source title").copy(publishedDate = "2025-06-23",
            identifiers = mapOf("remote" to "new")), setOf(MetadataField.PUBLISHED_DATE, MetadataField.IDENTIFIERS))
        assertEquals("2025-06-23", result.publishedDate)
        assertEquals(mapOf("local" to "keep", "remote" to "new"), result.identifiers)
        assertEquals("My title", result.title)
    }
    @Test fun uncheckedIsbnCannotBeOverwrittenThroughProviderIdentifiers() {
        val incoming = emptySourceMetadata("Source").copy(isbn = "9780765377104",
            identifiers = mapOf("bnf_isbn" to "9780765377104", "bnf" to "book"))
        val result = current.applySelectedMetadata(incoming, MetadataField.defaultSelection - MetadataField.ISBN)
        assertEquals(current.isbn, result.isbn)
        assertFalse("bnf_isbn" in result.identifiers)
        assertEquals("book", result.identifiers["bnf"])
    }
    @Test fun seriesNumbersAreOnlyTakenFromExplicitSeriesInformation() {
        assertEquals("The Expanse" to 5.0, "The Expanse ; 5".seriesDetails())
        assertEquals("The Three-Body Problem" to 3.0, "The Three-Body Problem, book 3".seriesDetails())
        assertEquals("Fiction 2000" to null, "Fiction 2000".seriesDetails())
    }
    @Test fun languageNamesAreLocalizedWhileEpubCodesKeepRegionsAndScripts() {
        val pt = Locale.forLanguageTag("pt-BR")
        assertEquals("Português (Brasil)", MetadataLanguages.display("pt-BR", pt))
        assertEquals("Inglês", MetadataLanguages.display("eng", pt))
        assertEquals("pt", MetadataLanguages.code("Português"))
        assertEquals("en", MetadataLanguages.code("English"))
        assertEquals("fr", MetadataLanguages.code("/languages/fre"))
        assertEquals("nl", MetadataLanguages.code("dut"))
        assertEquals("pt-BR", MetadataLanguages.fromDisplay("Português (Brasil)", "pt-BR", pt))
        assertEquals("zh-Hant", MetadataLanguages.code("zh_Hant"))
        assertEquals("Unrecognized", MetadataLanguages.display("Unrecognized", pt))
    }
    @Test fun aScanListingMultipleEditionIsbnsCannotMixTheirPublishersOrDates() {
        val edition = MetadataLookupCandidate("ol", "Open Library", current.copy(authors = emptyList()), null, sourceUrl = null,
            matchedIsbns = setOf("9781466853454"), sourceLanguage = "pt")
        val scan = edition.copy(id = "ia", sourceName = "Internet Archive", metadata = current.copy(publisher = "Wrong edition", publishedDate = "1965"),
            matchedIsbns = setOf("9781466853454", "9780765377104"))
        val result = MetadataSearchRules.rank(listOf(scan, edition), current, 12)
        assertEquals(2, result.size)
        assertEquals("ol", result.first().id)
        assertEquals(current.publishedDate, result.first().metadata.publishedDate)
    }
    @Test fun directCatalogEditionOutranksAMultiEditionScanBeforeItsAuthorsAreResolved() {
        val edition = MetadataLookupCandidate("ol", "Open Library", current.copy(authors = emptyList()), null,
            sourceUrl = null, matchedIsbns = setOf(current.isbn!!, "9780765377104"), editionResolved = true)
        val scan = edition.copy(id = "ia", sourceName = "Internet Archive", metadata = current,
            editionResolved = false, matchedIsbns = edition.matchedIsbns + "9780441172719")
        assertEquals("ol", MetadataSearchRules.rank(listOf(scan, edition), current, 12).first().id)
    }
    @Test fun matchingEditionsComplementEachOtherWithoutMixingDifferentIsbns() {
        fun candidate(id: String, isbn: String, metadata: com.foliore.shared.domain.BookMetadataDraft) =
            MetadataLookupCandidate(id, id, metadata, null, sourceUrl = null, matchedIsbns = setOf(isbn), sourceLanguage = "pt")
        val preferred = candidate("one", "9781466853454", current.copy(publisher = null))
        val complement = candidate("two", "9781466853454", current.copy(publisher = "Source publisher", description = "Other description"))
        val different = candidate("three", "9780765377104", current.copy(publisher = "Different edition"))
        val result = MetadataSearchRules.rank(listOf(preferred, complement, different), current, 12)
        assertEquals(2, result.size)
        assertEquals("Source publisher", result.first().metadata.publisher)
        assertEquals(2, result.first().sourceName.split(" · ").size)
        assertEquals("Different edition", result.last().metadata.publisher)
    }
}
