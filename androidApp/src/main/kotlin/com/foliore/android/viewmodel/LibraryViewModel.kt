package com.foliore.android.viewmodel

import android.net.Uri
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.BatchBookEditResult
import com.foliore.android.storage.BookEditResult
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.BookDeleteResult
import com.foliore.android.storage.EpubImportResult
import com.foliore.android.storage.ManualSyncResult
import com.foliore.android.storage.ManualSyncService
import com.foliore.android.storage.PreparedEpubResult
import com.foliore.shared.domain.BatchMetadataPatch
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val epubImporter: AndroidEpubImporter,
    private val bookEditor: AndroidBookEditor,
    private val bookDeleter: AndroidBookDeleter,
    private val preparedEpubService: AndroidPreparedEpubService,
    private val manualSyncService: ManualSyncService,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<LibraryNotice?>(null)
    val notice: StateFlow<LibraryNotice?> = _notice.asStateFlow()

    init {
        refresh()
        refreshSyncFolder()
    }

    fun refresh() {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            _state.update { it.withBooks(books) }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }

            var imported = 0
            var duplicates = 0
            var failed = 0

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    when (epubImporter.import(uri)) {
                        is EpubImportResult.Imported -> imported += 1
                        is EpubImportResult.Duplicate -> duplicates += 1
                        is EpubImportResult.Failed -> failed += 1
                    }
                }
            }

            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            _state.update { it.withBooks(books).copy(isImporting = false) }
            _notice.value = LibraryNotice(
                type = when {
                    imported > 0 && duplicates == 0 && failed == 0 -> LibraryNoticeType.IMPORTED
                    imported == 0 && duplicates > 0 && failed == 0 -> LibraryNoticeType.DUPLICATE
                    imported == 0 && failed > 0 -> LibraryNoticeType.FAILED
                    else -> LibraryNoticeType.MIXED
                },
            )
        }
    }

    fun setSearchOpen(isOpen: Boolean) {
        _state.update { it.copy(isSearchOpen = isOpen, query = if (isOpen) it.query else "") }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setFilter(filter: BookFilter) {
        _state.update { it.copy(activeFilter = filter) }
    }

    fun setSortOrder(sortOrder: BookSortOrder) {
        _state.update { it.copy(sortOrder = sortOrder) }
    }

    fun setSeriesFilter(seriesFilter: SeriesFilter) {
        _state.update { it.copy(seriesFilter = seriesFilter) }
    }

    fun setGridColumns(gridColumns: BookGridColumns) {
        _state.update { it.copy(gridColumns = gridColumns) }
    }

    fun setBatchMode(isEnabled: Boolean) {
        _state.update {
            it.copy(
                isBatchMode = isEnabled,
                selectedBookIds = if (isEnabled) it.selectedBookIds else emptySet(),
            )
        }
    }

    fun toggleBookSelection(bookId: String) {
        _state.update { current ->
            val selectedBookIds = current.selectedBookIds.toMutableSet().apply {
                if (!add(bookId)) remove(bookId)
            }
            current.copy(
                isBatchMode = true,
                selectedBookIds = selectedBookIds,
            )
        }
    }

    fun selectBooks(bookIds: List<String>) {
        if (bookIds.isEmpty()) return
        _state.update { current ->
            current.copy(
                isBatchMode = true,
                selectedBookIds = current.selectedBookIds + bookIds,
            )
        }
    }

    fun applyBatchMetadata(
        patch: BatchMetadataPatch,
        onApplied: () -> Unit,
    ) {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedBookIds
            val orderedIds = _state.value.books
                .map { it.book.id }
                .filter { it in selectedIds }
            val result = withContext(Dispatchers.IO) {
                bookEditor.updateMetadataBatch(orderedIds, patch)
            }
            refreshBooks(clearBatchSelection = result is BatchBookEditResult.Success)
            _notice.value = LibraryNotice(
                when (result) {
                    is BatchBookEditResult.Success -> LibraryNoticeType.BATCH_UPDATED
                    BatchBookEditResult.EmptySelection,
                    BatchBookEditResult.Failed -> LibraryNoticeType.BATCH_FAILED
                    BatchBookEditResult.NoChanges -> LibraryNoticeType.BATCH_NO_CHANGES
                },
            )
            if (result is BatchBookEditResult.Success) {
                onApplied()
            }
        }
    }

    fun saveMetadataAndPrepare(
        bookId: String,
        draft: BookMetadataDraft,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bookEditor.updateMetadata(bookId, draft)
            }
            when (result) {
                is BookEditResult.Success -> {
                    val prepareResult = withContext(Dispatchers.IO) {
                        preparedEpubService.prepare(bookId)
                    }
                    refreshBooks()
                    if (prepareResult is PreparedEpubResult.Prepared) {
                        _notice.value = LibraryNotice(LibraryNoticeType.METADATA_SAVED)
                        onSaved()
                    } else {
                        _notice.value = LibraryNotice(LibraryNoticeType.PREPARE_FAILED)
                    }
                }
                BookEditResult.InvalidTitle -> {
                    refreshBooks()
                    _notice.value = LibraryNotice(LibraryNoticeType.INVALID_TITLE)
                }
                BookEditResult.Failed,
                BookEditResult.NotFound -> {
                    refreshBooks()
                    _notice.value = LibraryNotice(LibraryNoticeType.UPDATE_FAILED)
                }
            }
        }
    }

    fun replaceCover(
        bookId: String,
        imageUri: Uri,
        onUpdated: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bookEditor.replaceCover(bookId, imageUri)
            }
            refreshBooks()
            _notice.value = LibraryNotice(
                when (result) {
                    is BookEditResult.Success -> LibraryNoticeType.COVER_UPDATED
                    BookEditResult.InvalidTitle,
                    BookEditResult.Failed,
                    BookEditResult.NotFound -> LibraryNoticeType.UPDATE_FAILED
                },
            )
            if (result is BookEditResult.Success) {
                onUpdated()
            }
        }
    }

    fun deleteBook(
        bookId: String,
        onDeleted: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bookDeleter.delete(bookId)
            }
            refreshBooks()
            _notice.value = LibraryNotice(
                when (result) {
                    is BookDeleteResult.Deleted -> LibraryNoticeType.BOOK_DELETED
                    BookDeleteResult.Failed,
                    BookDeleteResult.NotFound -> LibraryNoticeType.DELETE_FAILED
                },
            )
            if (result is BookDeleteResult.Deleted) {
                onDeleted()
            }
        }
    }

    fun sharePreparedEpub(
        bookId: String,
        onShareIntentReady: (Intent) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                preparedEpubService.share(bookId)
            }
            refreshBooks()
            when (result) {
                is PreparedEpubResult.ShareIntentReady -> onShareIntentReady(result.intent)
                else -> _notice.value = LibraryNotice(LibraryNoticeType.EXPORT_FAILED)
            }
        }
    }

    fun configureSyncFolder(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                manualSyncService.configureFolder(uri)
            }
            val folderLabel = when (result) {
                is ManualSyncResult.Configured -> result.folderLabel
                else -> withContext(Dispatchers.IO) { manualSyncService.folderLabel() }
            }
            _state.update {
                it.copy(syncFolderLabel = folderLabel)
            }
            _notice.value = LibraryNotice(
                when (result) {
                    is ManualSyncResult.Configured -> LibraryNoticeType.SYNC_FOLDER_CONFIGURED
                    else -> LibraryNoticeType.SYNC_FAILED
                },
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            val result = withContext(Dispatchers.IO) {
                manualSyncService.syncNow()
            }
            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            val folderLabel = withContext(Dispatchers.IO) {
                manualSyncService.folderLabel()
            }
            _state.update {
                it.withBooks(books).copy(
                    books = books,
                    isSyncing = false,
                    syncFolderLabel = folderLabel,
                    lastSyncSummary = (result as? ManualSyncResult.Synced)?.let { synced ->
                        SyncSummary(
                            uploadedBooks = synced.uploadedBooks,
                            downloadedBooks = synced.downloadedBooks,
                            conflictedBooks = synced.conflictedBooks,
                        )
                    },
                )
            }
            _notice.value = LibraryNotice(result.toNoticeType())
        }
    }

    fun disconnectSync() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                manualSyncService.disconnect()
            }
            _state.update { it.copy(syncFolderLabel = null, isSyncing = false) }
            _notice.value = LibraryNotice(LibraryNoticeType.SYNC_DISCONNECTED)
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    private suspend fun refreshBooks(clearBatchSelection: Boolean = false) {
        val books = withContext(Dispatchers.IO) {
            libraryRepository.getBooks()
        }
        _state.update { current ->
            if (clearBatchSelection) {
                current.copy(
                    books = books,
                    isImporting = false,
                    isBatchMode = false,
                    selectedBookIds = emptySet(),
                )
            } else {
                current.withBooks(books).copy(isImporting = false)
            }
        }
    }

    private fun refreshSyncFolder() {
        viewModelScope.launch {
            val folderLabel = withContext(Dispatchers.IO) {
                manualSyncService.folderLabel()
            }
            _state.update { it.copy(syncFolderLabel = folderLabel) }
        }
    }

    private fun ManualSyncResult.toNoticeType(): LibraryNoticeType {
        return when (this) {
            ManualSyncResult.NotConfigured -> LibraryNoticeType.SYNC_NOT_CONFIGURED
            ManualSyncResult.FolderUnavailable -> LibraryNoticeType.SYNC_FOLDER_UNAVAILABLE
            ManualSyncResult.Failed -> LibraryNoticeType.SYNC_FAILED
            is ManualSyncResult.Configured -> LibraryNoticeType.SYNC_FOLDER_CONFIGURED
            is ManualSyncResult.Synced -> {
                if (conflictedBooks > 0) {
                    LibraryNoticeType.SYNC_COMPLETE_WITH_CONFLICTS
                } else {
                    LibraryNoticeType.SYNC_COMPLETE
                }
            }
        }
    }

    private fun LibraryUiState.withBooks(books: List<LibraryBook>): LibraryUiState {
        val currentIds = books.map { it.book.id }.toSet()
        val selectedIds = selectedBookIds.intersect(currentIds)
        return copy(
            books = books,
            selectedBookIds = selectedIds,
            isBatchMode = isBatchMode && selectedIds.isNotEmpty(),
        )
    }
}
