package com.foliore.android.metadata

import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*

class MetadataSeriesTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun fixture(name: String) = javaClass.getResource("/metadata-series/$name.json")!!.readText()

    @Test fun readsRomanNumbersCatalogSeparatorsAndDecimalPositions() {
        for ((text, expected) in mapOf(
            "Three-body trilogy -- Book III" to CatalogSeries("Three-body trilogy", 3.0),
            "The Expanse -- 5" to CatalogSeries("The Expanse", 5.0),
            "Dungeon Crawler Carl #1" to CatalogSeries("Dungeon Crawler Carl", 1.0),
            "Dungeon Crawler Carl T3" to CatalogSeries("Dungeon Crawler Carl", 3.0),
            "The Expanse, bk. 5" to CatalogSeries("The Expanse", 5.0),
            "Saga ; 0.5" to CatalogSeries("Saga", 0.5),
            "Book Two of Space Odyssey" to CatalogSeries("Space Odyssey", 2.0),
            "odyssey 2" to CatalogSeries("Odyssey", 2.0),
        )) assertEquals(expected, text.catalogSeries(), text)
    }

    @Test fun combinesSeriesWithExplicitSubtitleAndWorkAnnotations() {
        assertEquals(CatalogSeries("Uma Odisseia no Espaço", 2.0), catalogSeries(explicit = listOf("Uma Odisseia no Espaço"), titles = listOf("2010", "V.2")))
        assertEquals(CatalogSeries("Dungeon Crawler Carl", 3.0), catalogSeries(explicit = listOf("Dungeon Crawler Carl"), titles = listOf("The Dungeon Anarchist's Cookbook", "Dungeon Crawler Carl, Book 3")))
        assertEquals(CatalogSeries("The Three-Body Problem Series", 3.0), catalogSeries(titles = listOf("Death's End (The Three-Body Problem Series Book 3)")))
        assertEquals(CatalogSeries("Dungeon Crawler Carl"), catalogSeries(subjects = listOf("genre:LitRPG", "series:Dungeon Crawler Carl")))
    }

    @Test fun yearsAndEditionLabelsCannotBecomeSeriesPositions() {
        for (title in listOf("2001", "2010", "2061", "3001", "1984", "2001: A Space Odyssey", "Apollo 13"))
            assertNull(catalogSeries(titles = listOf(title)), title)
        assertNull("1st ed.".catalogSeries())
        assertEquals("Fiction 2000" to null, "Fiction 2000".seriesDetails())
        assertNull(catalogSeries(explicit = listOf("Space Odyssey"), titles = listOf("2001"))?.index)
        assertNull("Space Odyssey, books 1-4".catalogSeries()?.index)
    }

    private fun resolve(name: String, workKey: String, seriesName: String? = null): MetadataLookupCandidate {
        val urls = mutableListOf<String>()
        val candidate = MetadataLookupCandidate(name, "Open Library", emptySourceMetadata(name).copy(
            authors = listOf("Known author"), language = "en", publisher = "Selected edition", publishedDate = "2024", seriesName = seriesName,
        ), null, sourceUrl = null, workKey = workKey, editionResolved = true)
        val result = MetadataCandidateEnricher(File("unused")).enrichMetadata(candidate, getText = { url ->
            urls += url
            when (url) {
                "https://openlibrary.org$workKey.json" -> fixture("$name-work")
                "https://openlibrary.org$workKey/editions.json?limit=50" -> fixture("$name-editions")
                else -> error("Unexpected request $url")
            }
        })
        assertEquals(2, urls.size)
        assertEquals(candidate.metadata.publisher, result.metadata.publisher)
        assertEquals(candidate.metadata.publishedDate, result.metadata.publishedDate)
        return result
    }

    @Test fun completes2001FromAnotherEditionOfTheSameWorkWithoutUsingItsYearAsPosition() {
        val m = resolve("2001", "/works/OL17365W").metadata
        assertEquals("Space Odyssey", m.seriesName)
        assertEquals(1.0, m.seriesIndex)
    }
    @Test fun completes2010FromItsOwnWork() {
        val m = resolve("2010", "/works/OL17391W").metadata
        assertEquals("Odyssey", m.seriesName)
        assertEquals(2.0, m.seriesIndex)
    }
    @Test fun completesDungeonCrawlerCarlAcrossLanguagesUsingExplicitSeriesMembership() {
        val m = resolve("dungeon", "/works/OL24593432W", "Dungeon Crawler Carl").metadata
        assertEquals("Dungeon Crawler Carl", m.seriesName)
        assertEquals(1.0, m.seriesIndex)
    }
    @Test fun completesDungeonAnarchistsCookbookFromTheSameWork() {
        val m = resolve("cookbook", "/works/OL24848242W", "Dungeon Crawler Carl").metadata
        assertEquals("Dungeon Crawler Carl", m.seriesName)
        assertEquals(3.0, m.seriesIndex)
    }
    @Test fun completes2061FromItsOwnWork() {
        val m = resolve("2061", "/works/OL17408W").metadata
        assertEquals("Odyssey", m.seriesName)
        assertEquals(3.0, m.seriesIndex)
    }
    @Test fun completes3001WithoutConfusingTheChinesePublisherCollectionWithTheStorySequence() {
        val m = resolve("3001", "/works/OL17409W").metadata
        assertEquals("Odyssey", m.seriesName)
        assertEquals(4.0, m.seriesIndex)
    }
    @Test fun completesCarlsDoomsdayScenarioFromItsOwnWork() {
        val m = resolve("doomsday", "/works/OL24848193W", "Dungeon Crawler Carl").metadata
        assertEquals("Dungeon Crawler Carl", m.seriesName)
        assertEquals(2.0, m.seriesIndex)
    }
    @Test fun shortYearTitlesAcceptTheCatalogsFullSubtitle() {
        val candidate = MetadataLookupCandidate("2001", "test", emptySourceMetadata("2001: A Space Odyssey").copy(authors = listOf("Arthur C. Clarke")), null, sourceUrl = null)
        assertEquals(listOf(candidate), MetadataSearchRules.rank(listOf(candidate), emptySourceMetadata("2001").copy(authors = listOf("Arthur C. Clarke")), 12))
    }
    @Test fun conflictsAndUnrelatedWorksCannotSupplyASeriesPosition() {
        val data = json("""{"entries":[
          {"series":["Saga, Book 2"],"works":[{"key":"/works/OL1W"}]},
          {"series":["Saga, Book 3"],"works":[{"key":"/works/OL1W"}]},
          {"series":["Saga, Book 9"],"works":[{"key":"/works/OL2W"}]}]}""")
        assertNull(seriesFromWorkEditions("/works/OL1W", data, "Saga", "en"))
        assertNull(seriesFromWorkEditions("/works/OL3W", data, null, "en"))
        val current = MetadataLookupCandidate("a", "test", emptySourceMetadata("Title").copy(seriesName = "Story saga"), null, sourceUrl = null)
        val other = current.copy(id = "b", metadata = current.metadata.copy(seriesName = "Publisher collection", seriesIndex = 100.0))
        assertNull(current.complementWith(other).metadata.seriesIndex)
    }
    @Test fun missingSeriesLookupDoesNotDiscardTheExistingMetadata() {
        val candidate = MetadataLookupCandidate("missing", "Open Library", emptySourceMetadata("Title").copy(seriesName = "Saga", authors = listOf("Author")), null,
            sourceUrl = null, workKey = "/works/OL1W", editionResolved = true)
        assertEquals(candidate, MetadataCandidateEnricher(File("unused")).enrichMetadata(candidate, getText = { throw java.io.IOException() }))
    }
    @Test fun exactEditionCanBorrowOnlySeriesFromAnEquivalentEstablishedWork() {
        val draft = emptySourceMetadata("Carl’s Doomsday Scenario").copy(authors = listOf("Matt Dinniman"), isbn = "9780241816219", language = "en")
        val exact = MetadataLookupCandidate("new", "Open Library", draft.copy(publisher = "Selected publisher", publishedDate = "2025"), null,
            sourceUrl = null, workKey = "/works/OL999W", editionResolved = true, matchedIsbns = setOf("9780241816219"), workEditionCount = 1, sourceLanguage = "en")
        val established = exact.copy(id = "established", metadata = draft.copy(seriesName = "Dungeon Crawler Carl"),
            workKey = "/works/OL24848193W", matchedIsbns = emptySet(), workEditionCount = 8)
        val ranked = MetadataSearchRules.rank(listOf(exact, established), draft, 12)
        assertEquals("new", ranked.first().id)
        assertEquals(listOf("/works/OL24848193W"), ranked.first().seriesWorkKeys)
        val result = MetadataCandidateEnricher(File("unused")).enrichMetadata(ranked.first(), getText = { url ->
            when (url) {
                "https://openlibrary.org/works/OL999W.json" -> "{}"
                "https://openlibrary.org/works/OL999W/editions.json?limit=50" -> "{\"entries\":[]}"
                "https://openlibrary.org/works/OL24848193W.json" -> fixture("doomsday-work")
                "https://openlibrary.org/works/OL24848193W/editions.json?limit=50" -> fixture("doomsday-editions")
                else -> error("Unexpected URL: $url")
            }
        })
        assertEquals("Dungeon Crawler Carl", result.metadata.seriesName)
        assertEquals(2.0, result.metadata.seriesIndex)
        assertEquals("Selected publisher", result.metadata.publisher)
        assertEquals("2025", result.metadata.publishedDate)
        assertEquals(draft.isbn, result.metadata.isbn)
        assertEquals("established", MetadataSearchRules.rank(listOf(exact, established), draft.copy(isbn = null), 12).first().id)
    }
    @Test fun unrelatedAuthorsAndDifferentLanguageRecordsCannotProvideAlternativeSeriesWorks() {
        val draft = emptySourceMetadata("2001").copy(authors = listOf("Arthur C. Clarke"))
        val original = MetadataLookupCandidate("one", "test", draft, null, sourceUrl = null, workKey = "/works/OL1W", sourceLanguage = "en")
        val wrongAuthor = original.copy(id = "wrong", workKey = "/works/OL2W", metadata = draft.copy(authors = listOf("Someone else")))
        val otherLanguage = original.copy(id = "translated", workKey = "/works/OL3W", sourceLanguage = "fr")
        val result = MetadataSearchRules.rank(listOf(original, wrongAuthor, otherLanguage), draft, 12)
        assertTrue(result.first { it.id == "one" }.seriesWorkKeys.isEmpty())
    }
    @Test fun importedAuthorBylineCannotHideTheEstablished2061Work() {
        val draft = emptySourceMetadata("2061: Odyssey Three").copy(authors = listOf("Arthur C. Clarke"), language = "en")
        val result = searchOpenLibrary(draft, 12) { fixture("2061-search") } as MetadataLookupResult.Success
        assertEquals("/works/OL17408W", result.candidates.first().workKey)
        assertEquals("2061", result.candidates.first().metadata.title)
        assertEquals("By the Light of the Moon", "By the Light of the Moon".withoutAuthorByline(listOf("Dean Koontz")))
    }
}
