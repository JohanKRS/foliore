package com.foliore.android.viewmodel

import android.net.Uri
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.BookEditResult
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.BookDeleteResult
import com.foliore.android.storage.EpubImportResult
import com.foliore.android.storage.ManualSyncResult
import com.foliore.android.storage.ManualSyncService
import com.foliore.android.storage.PreparedEpubResult
import com.foliore.shared.domain.BookMetadataDraft
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
            _state.update { it.copy(books = books) }
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
            _state.update { it.copy(books = books, isImporting = false) }
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

    fun saveMetadata(
        bookId: String,
        draft: BookMetadataDraft,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                bookEditor.updateMetadata(bookId, draft)
            }
            refreshBooks()
            when (result) {
                is BookEditResult.Success -> {
                    _notice.value = LibraryNotice(LibraryNoticeType.METADATA_SAVED)
                    onSaved()
                }
                BookEditResult.InvalidTitle -> {
                    _notice.value = LibraryNotice(LibraryNoticeType.INVALID_TITLE)
                }
                BookEditResult.Failed,
                BookEditResult.NotFound -> {
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

    fun prepareEpub(bookId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                preparedEpubService.prepare(bookId)
            }
            refreshBooks()
            _notice.value = LibraryNotice(
                when (result) {
                    is PreparedEpubResult.Prepared -> LibraryNoticeType.EPUB_PREPARED
                    else -> LibraryNoticeType.PREPARE_FAILED
                },
            )
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
                it.copy(
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

    private suspend fun refreshBooks() {
        val books = withContext(Dispatchers.IO) {
            libraryRepository.getBooks()
        }
        _state.update { it.copy(books = books, isImporting = false) }
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
}
