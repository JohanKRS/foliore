package com.foliore.android.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.PreparedEpubResult
import com.foliore.android.storage.SyncOperationCoordinator
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.LibraryBook
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

internal data class ReaderNavigation(val section: Int, val locator: ReadingLocator, val fragment: String? = null, val user: Boolean = false, val token: Long = System.nanoTime())
internal data class ReaderState(
    val book: LibraryBook? = null,
    val publication: EpubPublication? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val navigation: ReaderNavigation? = null,
    val locator: ReadingLocator? = null,
    val page: Int = 1,
    val pages: Int = 1,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val bookmarks: List<ReadingBookmark> = emptyList(),
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
)

internal class ReaderViewModel(context: Context, private val bookId: String) : ViewModel() {
    private val container = AppContainer(context)
    private val repository = ReadingStateRepository(context)
    private val mutable = MutableStateFlow(ReaderState(preferences = repository.preferences()))
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var lastPosition: ReadingPosition? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        mutable.value = mutable.value.copy(loading = true, failed = false)
        loadJob = viewModelScope.launch {
            var opened: EpubPublication? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    synchronized(SyncOperationCoordinator.localDataLock) {
                        var book = checkNotNull(container.libraryRepository.getBook(bookId))
                        if (book.book.preparedFilePath?.let { File(it).isFile } != true || book.book.status == BookStatus.OUTDATED_PREPARED) {
                            check(container.preparedEpubService.prepare(bookId) is PreparedEpubResult.Prepared)
                            book = checkNotNull(container.libraryRepository.getBook(bookId))
                        }
                        val publication = EpubPublication.open(File(checkNotNull(book.book.preparedFilePath)))
                        opened = publication
                        book to publication
                    }
                }
                val (book, publication) = result
                val stored = repository.position(bookId, book.book.originalHashSha256)
                val section = publication.sections.indexOfFirst { it.href == stored?.locator?.href }.coerceAtLeast(0)
                val loc = stored?.locator?.takeIf { it.href == publication.sections[section].href } ?: ReadingLocator(publication.sections[section].href)
                lastPosition = stored
                mutable.value.publication?.close()
                mutable.value = mutable.value.copy(book = book, publication = publication, loading = false, navigation = ReaderNavigation(section, loc), locator = loc,
                    bookmarks = repository.bookmarks(bookId, book.book.originalHashSha256))
                opened = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, failed = true) }
            finally { opened?.close() }
        }
    }

    fun position(locator: ReadingLocator, page: Int, pages: Int, user: Boolean) {
        val state = mutable.value
        if (!locator.valid() || locator.href != state.navigation?.locator?.href) return
        mutable.value = state.copy(locator = locator, page = page.coerceAtLeast(1), pages = pages.coerceAtLeast(1), navigation = state.navigation?.copy(fragment = null, user = false))
        if (user && state.book != null) {
            repository.savePosition(bookId, state.book.book.originalHashSha256, locator)
            lastPosition = repository.position(bookId, state.book.book.originalHashSha256)
        }
    }

    fun navigate(section: Int, locator: ReadingLocator? = null, fragment: String? = null) {
        val publication = mutable.value.publication ?: return
        if (section !in publication.sections.indices) return
        val target = locator ?: ReadingLocator(publication.sections[section].href)
        mutable.value = mutable.value.copy(navigation = ReaderNavigation(section, target, fragment, true), locator = target)
    }

    fun chapter(direction: Int) {
        val state = mutable.value
        val current = state.navigation?.section ?: return
        val sections = state.publication?.sections ?: return
        val next = current + direction
        if (next !in sections.indices) return
        navigate(next, ReadingLocator(sections[next].href, progression = if (direction < 0) 1.0 else 0.0))
    }

    fun settings(value: ReaderPreferences) { repository.savePreferences(value); mutable.value = mutable.value.copy(preferences = value.normalized()) }
    fun bookmark() {
        val state = mutable.value; val book = state.book ?: return; val loc = state.locator ?: return
        val title = state.publication?.sections?.getOrNull(state.navigation?.section ?: 0)?.title.orEmpty()
        repository.addBookmark(bookId, book.book.originalHashSha256, loc, title)
        refreshBookmarks()
    }
    fun deleteBookmark(id: String) { repository.deleteBookmark(id); refreshBookmarks() }
    private fun refreshBookmarks() { val book = mutable.value.book ?: return; mutable.value = mutable.value.copy(bookmarks = repository.bookmarks(bookId, book.book.originalHashSha256)) }

    fun resume() {
        val state = mutable.value; val book = state.book ?: return
        val stored = repository.position(bookId, book.book.originalHashSha256)
        if (stored != null && stored != lastPosition) {
            val section = state.publication?.sections?.indexOfFirst { it.href == stored.locator.href } ?: -1
            if (section >= 0) mutable.value = state.copy(navigation = ReaderNavigation(section, stored.locator), locator = stored.locator)
            lastPosition = stored
        }
        refreshBookmarks()
    }

    fun search(query: String) {
        searchJob?.cancel()
        mutable.value = mutable.value.copy(searchResults = emptyList(), searching = query.trim().length >= 2, searchFailed = false)
        if (query.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val publication = mutable.value.publication ?: return@launch
                val needle = query.trim().take(160)
                val results = withContext(Dispatchers.Default) {
                    buildList {
                        for (index in publication.sections.indices) {
                            ensureActive()
                            val text = publication.text(index)
                            var from = 0
                            while (size < 250) {
                                val offset = text.indexOf(needle, from, ignoreCase = true)
                                if (offset < 0) break
                                add(ReaderSearchResult(index, offset, text.substring((offset - 45).coerceAtLeast(0), (offset + needle.length + 80).coerceAtMost(text.length)).replace(Regex("\\s+"), " "), text.substring(offset, offset + needle.length)))
                                from = offset + needle.length
                            }
                            if (size >= 250) break
                        }
                    }
                }
                mutable.value = mutable.value.copy(searching = false, searchResults = results)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(searching = false, searchFailed = true) }
        }
    }

    override fun onCleared() { searchJob?.cancel(); mutable.value.publication?.close() }
}
