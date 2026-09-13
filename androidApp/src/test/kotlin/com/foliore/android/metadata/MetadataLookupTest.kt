package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import kotlin.test.*
import kotlinx.serialization.json.*

class MetadataLookupTest {
    private val draft = BookMetadataDraft("Death's End", null, listOf("Cixin Liu"), null, "en-US", "Tor", "2016", "Existing description", emptyList(), "9781466853454", emptyMap(), null, null)
    private fun candidate(id: String, title: String = draft.title, author: String = "Cixin Liu", language: String = "en", matched: Set<String> = emptySet()) =
        MetadataLookupCandidate(id, "test", draft.copy(title = title, authors = listOf(author), language = language), null, sourceUrl = null, matchedIsbns = matched, sourceLanguage = language)

    @Test fun validatesIsbnChecksumsAndNormalizesEquivalentEditions() {
        assertEquals("9781466853454", MetadataSearchRules.isbn("ISBN-10: 146685345X"))
        assertEquals("9781466853454", MetadataSearchRules.isbn("978-1-4668-5345-4"))
        for (invalid in listOf("9781466853455", "1466853451", "uuid-9781466853454", "1234567890")) assertNull(MetadataSearchRules.isbn(invalid))
        assertNull(MetadataSearchRules.bestIsbn(draft.copy(isbn = null, identifiers = mapOf("other" to "9781466853454"))))
    }
    @Test fun exactIsbnWinsAndUnrelatedTitlesAreFiltered() {
        val exact = candidate("exact", language = "zh", matched = setOf("9781466853454"))
        val wrong = candidate("wrong", title = "Gardening for beginners", author = "Jane Doe")
        assertEquals("exact", MetadataSearchRules.rank(listOf(candidate("title"), wrong, exact), draft, 8).first().id)
        assertFalse(MetadataSearchRules.rank(listOf(wrong), draft, 8).isNotEmpty())
    }
    @Test fun ranksBothProvidersByAuthorAndLanguageRatherThanProviderOrder() {
        val bad = candidate("first-provider", author = "Someone Else", language = "fr")
        val good = candidate("second-provider")
        assertEquals("second-provider", MetadataSearchRules.rank(listOf(bad, good), draft, 8).first().id)
    }
    @Test fun inheritedIsbnDoesNotCollapseDistinctEditions() {
        val one = candidate("one").copy(metadata = draft.copy(publisher = "Tor"))
        val two = candidate("two").copy(metadata = draft.copy(publisher = "Head of Zeus"))
        assertEquals(2, MetadataSearchRules.rank(listOf(one, two), draft, 8).size)
        assertEquals(1, MetadataSearchRules.rank(listOf(one.copy(matchedIsbns = setOf("9781466853454")), two.copy(matchedIsbns = setOf("9781466853454"))), draft, 8).size)
    }
    @Test fun queriesQuotePhrasesAndOfferShortTitlesAndAliases() {
        assertEquals(listOf("A Book: A subtitle", "A Book"), MetadataSearchRules.titleVariants("A Book: A subtitle"))
        assertEquals("pt", MetadataSearchRules.language("pt-BR"))
        assertEquals("pt", MetadataSearchRules.language("/languages/por"))
    }
    @Test fun openLibraryUsesTheExactEditionRatherThanWorkLevelArrays() {
        val result = searchOpenLibrary(draft, 8) { url ->
            if (!url.endsWith("/isbn/9781466853454.json")) return@searchOpenLibrary """{"docs":[]}"""
            """{"key":"/books/OL26214600M","title":"Death's End","isbn_13":["9781466853454"],"publishers":["Tor Books"],"publish_date":"2016","covers":[10745175],"languages":[{"key":"/languages/eng"}],"works":[{"key":"/works/OL17319605W"}]}"""
        } as MetadataLookupResult.Success
        val metadata = result.candidates.single().metadata
        assertEquals("Tor Books", metadata.publisher)
        assertEquals("en", metadata.language)
        assertEquals("2016", metadata.publishedDate)
        assertEquals("9781466853454", metadata.isbn)
        assertEquals(listOf("https://covers.openlibrary.org/b/id/10745175-L.jpg?default=false"), result.candidates.single().coverUrls)
    }
    @Test fun openLibraryRetriesTitleWithoutAuthorAndDoesNotMixWorkEditionFields() {
        val urls = mutableListOf<String>()
        val result = searchOpenLibrary(draft.copy(isbn = null), 8) { url ->
            urls += url
            if ("&author=" in url) """{"docs":[]}""" else
                """{"docs":[{"key":"/works/OL17319605W","title":"死神永生","author_name":["Cixin Liu"],"language":["fra","eng"],"publisher":["Wrong publisher"],"isbn":["9780765386632"],"first_publish_year":2010,"editions":{"docs":[{"key":"/books/OL26214600M","title":"Death's End","language":["eng"]}]}}]}"""
        } as MetadataLookupResult.Success
        assertEquals(2, urls.size)
        val candidate = result.candidates.single()
        assertEquals("Death's End", candidate.metadata.title)
        assertNull(candidate.metadata.publisher)
        assertNull(candidate.metadata.publishedDate)
        assertNull(candidate.metadata.isbn)
        assertTrue(candidate.matchedIsbns.isEmpty())
    }
    @Test fun failureIsNotReportedAsNoMatchesAndPartialSuccessSurvives() {
        val failure = { MetadataLookupResult.Failure("offline") }
        assertIs<MetadataLookupResult.Failure>(collectMetadataResults(draft, 8, listOf(failure, failure)))
        assertIs<MetadataLookupResult.Failure>(searchOpenLibrary(draft, 8) { if ("/isbn/" in it) throw MetadataHttpException(404) else throw java.io.IOException("offline") })
        val result = collectMetadataResults(draft, 8, listOf(failure, { MetadataLookupResult.Success(listOf(candidate("good"))) })) as MetadataLookupResult.Success
        assertEquals("good", result.candidates.single().id)
    }
    @Test fun slowProviderCannotDiscardSuccessfulResultsOrExceedSearchDeadline() {
        val start = System.nanoTime()
        val result = collectMetadataResults(draft, 8, listOf(
            { Thread.sleep(2000); MetadataLookupResult.Success(emptyList()) },
            { MetadataLookupResult.Success(listOf(candidate("good"))) },
        ), timeoutMillis = 150) as MetadataLookupResult.Success
        assertEquals("good", result.candidates.single().id)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
    }
    @Test fun emptyResultIsValidWhenOnlyAnUnconfiguredOptionalProviderWasSkipped() {
        assertIs<MetadataLookupResult.Success>(collectMetadataResults(draft, 8, listOf(
            { MetadataLookupResult.Failure(notConfigured = true) }, { MetadataLookupResult.Success(emptyList()) },
        )))
    }
    @Test fun relatedBooksDoNotPassForTheRequestedNovel() {
        val request = draft.copy(title = "Outlander aka Cross Stitch", authors = listOf("Diana Gabaldon"), isbn = null)
        val good = candidate("novel", title = "Outlander", author = "Diana Gabaldon")
        val related = listOf("Virgins (Outlander 0.5)", "Outlander Collection", "The Official Outlander Coloring Book")
            .mapIndexed { i, title -> candidate("related$i", title, "Diana Gabaldon") }
        assertEquals(listOf("novel"), MetadataSearchRules.rank(related + good, request, 8).map { it.id })
    }
    @Test fun graphicAdaptationsDoNotMatchTheNovelJustBecauseTheTitleBeforeColonMatches() {
        val request = draft.copy(title = "Dune", authors = listOf("Frank Herbert"), isbn = null)
        val graphic = candidate("graphic", title = "Dune: The Graphic Novel", author = "Frank Herbert")
        val spinOff = candidate("spinoff", title = "Dune: Maison Harkonnen", author = "Frank Herbert")
        assertTrue(MetadataSearchRules.rank(listOf(spinOff), request, 12).isEmpty())
        assertTrue(MetadataSearchRules.rank(listOf(graphic), request, 12).isEmpty())
        assertEquals(1, MetadataSearchRules.rank(listOf(graphic), request.copy(title = "Dune: The Graphic Novel"), 12).size)
    }
    @Test fun confirmedLanguageOutranksLanguageInheritedFromTheEpub() {
        val unknown = candidate("a-unknown").copy(sourceLanguage = null)
        assertEquals("confirmed", MetadataSearchRules.rank(listOf(unknown, candidate("confirmed")), draft, 8).first().id)
    }
    @Test fun missingIsbnEditionAndSuccessfulEmptySearchAreNotAnOutage() {
        val result = searchOpenLibrary(draft, 8) { url ->
            if ("/isbn/" in url) throw MetadataHttpException(404)
            else """{"docs":[]}"""
        }
        assertEquals(emptyList(), assertIs<MetadataLookupResult.Success>(result).candidates)
    }

}
