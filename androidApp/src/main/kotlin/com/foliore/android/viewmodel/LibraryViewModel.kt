package com.foliore.android.viewmodel

import android.net.Uri
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.android.ai.AiCoverStyleRequest
import com.foliore.android.ai.AiGeneratedCoverOption
import com.foliore.android.ai.AiProviderMode
import com.foliore.android.ai.CodexBridgeService
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.ai.OpenAiResult
import com.foliore.android.ai.isEnabled
import com.foliore.android.storage.AiCoverPromptRepository
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.BatchBookEditResult
import com.foliore.android.storage.BookEditResult
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.BookDeleteResult
import com.foliore.android.storage.EpubImportResult
import com.foliore.android.storage.ManualSyncPhase
import com.foliore.android.storage.ManualSyncProgress
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
import java.io.File

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val epubImporter: AndroidEpubImporter,
    private val bookEditor: AndroidBookEditor,
    private val bookDeleter: AndroidBookDeleter,
    private val preparedEpubService: AndroidPreparedEpubService,
    private val manualSyncService: ManualSyncService,
    private val aiPreferenceRepository: AiPreferenceRepository,
    private val aiCoverPromptRepository: AiCoverPromptRepository,
    private val openAiApiService: OpenAiApiService,
    private val codexBridgeService: CodexBridgeService,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LibraryUiState(
            aiProviderMode = aiPreferenceRepository.providerMode.value,
            hasOpenAiApiKey = aiPreferenceRepository.hasOpenAiApiKey.value,
            codexBridgeBaseUrl = aiPreferenceRepository.codexBridgeBaseUrl.value,
            hasCodexBridgeToken = aiPreferenceRepository.hasCodexBridgeToken.value,
            aiCoverPromptRecords = aiCoverPromptRepository.records(),
        ),
    )
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
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = SyncProgress(
                        phase = SyncProgressPhase.IMPORTING,
                        current = 0,
                        total = uris.size,
                    ),
                )
            }

            var imported = 0
            var duplicates = 0
            var failed = 0

            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    when (epubImporter.import(uri)) {
                        is EpubImportResult.Imported -> imported += 1
                        is EpubImportResult.Duplicate -> duplicates += 1
                        is EpubImportResult.Failed -> failed += 1
                    }
                    _state.update {
                        it.copy(
                            importProgress = SyncProgress(
                                phase = SyncProgressPhase.IMPORTING,
                                current = index + 1,
                                total = uris.size,
                            ),
                        )
                    }
                }
            }

            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            _state.update { it.withBooks(books).copy(isImporting = false, importProgress = null) }
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

    fun setAiProviderMode(providerMode: AiProviderMode) {
        aiPreferenceRepository.setProviderMode(providerMode)
        _state.update { it.copy(aiProviderMode = providerMode) }
        _notice.value = LibraryNotice(LibraryNoticeType.AI_SETTINGS_UPDATED)
    }

    fun saveOpenAiApiKey(apiKey: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                aiPreferenceRepository.saveOpenAiApiKey(apiKey)
            }
            _state.update { it.copy(hasOpenAiApiKey = aiPreferenceRepository.hasOpenAiApiKey.value) }
            _notice.value = LibraryNotice(
                if (saved) {
                    LibraryNoticeType.AI_API_KEY_SAVED
                } else {
                    LibraryNoticeType.AI_REQUEST_FAILED
                },
            )
        }
    }

    fun clearOpenAiApiKey() {
        aiPreferenceRepository.clearOpenAiApiKey()
        _state.update { it.copy(hasOpenAiApiKey = false) }
        _notice.value = LibraryNotice(LibraryNoticeType.AI_API_KEY_CLEARED)
    }

    fun saveCodexBridgeSettings(baseUrl: String, token: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                aiPreferenceRepository.saveCodexBridgeSettings(baseUrl, token)
            }
            _state.update {
                it.copy(
                    codexBridgeBaseUrl = aiPreferenceRepository.codexBridgeBaseUrl.value,
                    hasCodexBridgeToken = aiPreferenceRepository.hasCodexBridgeToken.value,
                )
            }
            _notice.value = LibraryNotice(
                if (saved) {
                    LibraryNoticeType.AI_BRIDGE_SETTINGS_SAVED
                } else {
                    LibraryNoticeType.AI_REQUEST_FAILED
                },
            )
        }
    }

    fun clearCodexBridgeSettings() {
        aiPreferenceRepository.clearCodexBridgeSettings()
        _state.update {
            it.copy(
                codexBridgeBaseUrl = null,
                hasCodexBridgeToken = false,
            )
        }
        _notice.value = LibraryNotice(LibraryNoticeType.AI_BRIDGE_SETTINGS_CLEARED)
    }

    fun testCodexBridgeConnection() {
        if (!hasUsableCodexBridgeProvider()) {
            _notice.value = LibraryNotice(LibraryNoticeType.AI_PROVIDER_NOT_READY)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(importProgress = SyncProgress(SyncProgressPhase.PREPARING))
            }
            val result = withContext(Dispatchers.IO) {
                codexBridgeService.healthCheck()
            }
            _state.update { it.copy(importProgress = null) }
            _notice.value = LibraryNotice(
                when (result) {
                    is OpenAiResult.Success -> LibraryNoticeType.AI_BRIDGE_CONNECTED
                    is OpenAiResult.Failure -> result.toAiFailureNoticeType()
                },
            )
        }
    }

    fun requestMetadataAutofill() {
        _notice.value = LibraryNotice(
            if (!_state.value.aiProviderMode.isEnabled) {
                LibraryNoticeType.AI_PROVIDER_REQUIRED
            } else {
                LibraryNoticeType.AI_PROVIDER_NOT_READY
            },
        )
    }

    fun requestCoverGeneration() {
        _notice.value = LibraryNotice(
            if (!_state.value.aiProviderMode.isEnabled) {
                LibraryNoticeType.AI_COVER_PROVIDER_REQUIRED
            } else {
                LibraryNoticeType.AI_PROVIDER_NOT_READY
            },
        )
    }

    fun generateMetadataPreview(
        bookId: String,
        prompt: String,
        draft: BookMetadataDraft,
        onPreviewReady: (BookMetadataDraft) -> Unit,
    ) {
        if (!hasUsableAiMetadataProvider()) {
            requestMetadataAutofill()
            return
        }
        if (_state.value.hasActiveProgress) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    importProgress = SyncProgress(SyncProgressPhase.GENERATING_METADATA),
                )
            }
            val book = withContext(Dispatchers.IO) {
                libraryRepository.getBook(bookId)
            }
            val result = if (book == null) {
                OpenAiResult.Failure("Book not found")
            } else {
                withContext(Dispatchers.IO) {
                    when (_state.value.aiProviderMode) {
                        AiProviderMode.OPENAI_API -> openAiApiService.generateMetadataPreview(
                            book = book,
                            draft = draft,
                            prompt = prompt,
                        )
                        AiProviderMode.CODEX_BRIDGE_LOCAL,
                        AiProviderMode.CODEX_OAUTH_EXPERIMENTAL -> codexBridgeService.generateMetadataPreview(
                            book = book,
                            draft = draft,
                            prompt = prompt,
                        )
                        AiProviderMode.DISABLED -> OpenAiResult.Failure(code = "provider_disabled")
                    }
                }
            }
            _state.update { it.copy(importProgress = null) }
            when (result) {
                is OpenAiResult.Success -> {
                    onPreviewReady(result.value)
                    _notice.value = LibraryNotice(LibraryNoticeType.AI_METADATA_PREVIEW_READY)
                }
                is OpenAiResult.Failure -> {
                    _notice.value = LibraryNotice(result.toAiFailureNoticeType())
                }
            }
        }
    }

    fun generateCoverImages(
        bookId: String,
        prompt: String,
        draft: BookMetadataDraft,
        reference: AiCoverPromptRecord?,
        styles: List<AiCoverStyleRequest>,
        onOptionsReady: (List<AiGeneratedCoverOption>) -> Unit,
    ) {
        if (!hasUsableAiCoverProvider()) {
            requestCoverGeneration()
            return
        }
        if (_state.value.hasActiveProgress) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    importProgress = SyncProgress(
                        phase = SyncProgressPhase.GENERATING_COVERS,
                        current = 0,
                        total = styles.size,
                    ),
                )
            }
            val book = withContext(Dispatchers.IO) {
                libraryRepository.getBook(bookId)
            }
            val result = if (book == null) {
                OpenAiResult.Failure("Book not found")
            } else {
                withContext(Dispatchers.IO) {
                    when (_state.value.aiProviderMode) {
                        AiProviderMode.OPENAI_API -> openAiApiService.generateCoverOptions(
                            book = book,
                            draft = draft,
                            prompt = prompt,
                            reference = reference,
                            styles = styles,
                        )
                        AiProviderMode.CODEX_BRIDGE_LOCAL,
                        AiProviderMode.CODEX_OAUTH_EXPERIMENTAL -> codexBridgeService.generateCoverOptions(
                            book = book,
                            draft = draft,
                            prompt = prompt,
                            reference = reference,
                            styles = styles,
                        )
                        AiProviderMode.DISABLED -> OpenAiResult.Failure(code = "provider_disabled")
                    }
                }
            }
            _state.update { it.copy(importProgress = null) }
            when (result) {
                is OpenAiResult.Success -> {
                    onOptionsReady(result.value)
                    _notice.value = LibraryNotice(LibraryNoticeType.AI_COVER_OPTIONS_READY)
                }
                is OpenAiResult.Failure -> {
                    _notice.value = LibraryNotice(
                        if (result.code == "cover_generation_not_available") {
                            LibraryNoticeType.AI_BRIDGE_COVERS_UNAVAILABLE
                        } else {
                            result.toAiFailureNoticeType()
                        },
                    )
                }
            }
        }
    }

    fun saveSelectedCoverPrompt(record: AiCoverPromptRecord) {
        val records = aiCoverPromptRepository.save(record)
        _state.update { it.copy(aiCoverPromptRecords = records) }
        _notice.value = LibraryNotice(LibraryNoticeType.AI_COVER_PROMPT_SAVED)
    }

    fun useGeneratedCover(
        bookId: String,
        option: AiGeneratedCoverOption,
        record: AiCoverPromptRecord,
        onUpdated: () -> Unit = {},
    ) {
        val imagePath = option.imagePath
        if (imagePath == null) {
            requestCoverGeneration()
            return
        }

        replaceCover(
            bookId = bookId,
            imageUri = Uri.fromFile(File(imagePath)),
        ) {
            saveSelectedCoverPrompt(record)
            onUpdated()
        }
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

    fun deleteSelectedBooks() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedBookIds
            val orderedIds = _state.value.books
                .map { it.book.id }
                .filter { it in selectedIds }
            if (orderedIds.isEmpty()) return@launch

            val results = withContext(Dispatchers.IO) {
                orderedIds.map { bookId -> bookDeleter.delete(bookId) }
            }
            val deletedCount = results.count { it is BookDeleteResult.Deleted }
            val hasFailures = results.any { it !is BookDeleteResult.Deleted }
            refreshBooks(clearBatchSelection = deletedCount > 0 && !hasFailures)
            _notice.value = LibraryNotice(
                when {
                    deletedCount > 0 && !hasFailures -> LibraryNoticeType.BATCH_DELETED
                    deletedCount > 0 -> LibraryNoticeType.BATCH_DELETE_FAILED
                    else -> LibraryNoticeType.DELETE_FAILED
                },
            )
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
            when (result) {
                is BookEditResult.Success -> {
                    val prepareResult = withContext(Dispatchers.IO) {
                        preparedEpubService.prepare(bookId)
                    }
                    refreshBooks()
                    if (prepareResult is PreparedEpubResult.Prepared) {
                        _notice.value = LibraryNotice(LibraryNoticeType.COVER_UPDATED)
                        onUpdated()
                    } else {
                        _notice.value = LibraryNotice(LibraryNoticeType.PREPARE_FAILED)
                    }
                }
                BookEditResult.InvalidTitle,
                BookEditResult.Failed,
                BookEditResult.NotFound -> {
                    refreshBooks()
                    _notice.value = LibraryNotice(LibraryNoticeType.UPDATE_FAILED)
                }
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
        if (_state.value.hasActiveProgress) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncProgress = SyncProgress(SyncProgressPhase.PREPARING),
                )
            }
            val result = withContext(Dispatchers.IO) {
                manualSyncService.syncNow { progress ->
                    _state.update { current ->
                        current.copy(syncProgress = progress.toUiProgress())
                    }
                }
            }
            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            val folderLabel = withContext(Dispatchers.IO) {
                manualSyncService.folderLabel()
            }
            val aiCoverPromptRecords = withContext(Dispatchers.IO) {
                aiCoverPromptRepository.records()
            }
            _state.update {
                it.withBooks(books).copy(
                    books = books,
                    isSyncing = false,
                    syncProgress = null,
                    syncFolderLabel = folderLabel,
                    aiCoverPromptRecords = aiCoverPromptRecords,
                    lastSyncSummary = if (result is ManualSyncResult.Synced) {
                        SyncSummary(
                            uploadedBooks = result.uploadedBooks,
                            downloadedBooks = result.downloadedBooks,
                            conflictedBooks = result.conflictedBooks,
                        )
                    } else {
                        it.lastSyncSummary
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
            _state.update { it.copy(syncFolderLabel = null, isSyncing = false, syncProgress = null) }
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
                    importProgress = null,
                    isBatchMode = false,
                    selectedBookIds = emptySet(),
                )
            } else {
                current.withBooks(books).copy(isImporting = false, importProgress = null)
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

    private fun hasUsableOpenAiApiProvider(): Boolean {
        val current = _state.value
        return current.aiProviderMode == AiProviderMode.OPENAI_API && current.hasOpenAiApiKey
    }

    private fun hasUsableCodexBridgeProvider(): Boolean {
        val current = _state.value
        return (current.aiProviderMode == AiProviderMode.CODEX_BRIDGE_LOCAL ||
            current.aiProviderMode == AiProviderMode.CODEX_OAUTH_EXPERIMENTAL) &&
            current.codexBridgeBaseUrl != null &&
            current.hasCodexBridgeToken
    }

    private fun hasUsableAiMetadataProvider(): Boolean {
        return hasUsableOpenAiApiProvider() || hasUsableCodexBridgeProvider()
    }

    private fun hasUsableAiCoverProvider(): Boolean {
        return hasUsableOpenAiApiProvider() || hasUsableCodexBridgeProvider()
    }

    private fun OpenAiResult.Failure.toAiFailureNoticeType(): LibraryNoticeType {
        return when (code) {
            "missing_bridge_config" -> LibraryNoticeType.AI_PROVIDER_NOT_READY
            "unauthorized" -> LibraryNoticeType.AI_BRIDGE_UNAUTHORIZED
            "bridge_unreachable",
            "bridge_request_failed" -> LibraryNoticeType.AI_BRIDGE_UNREACHABLE
            "bridge_timeout",
            "codex_timeout" -> LibraryNoticeType.AI_BRIDGE_TIMEOUT
            "EPERM",
            "codex_spawn_failed",
            "codex_exit_failed",
            "codex_invalid_json" -> LibraryNoticeType.AI_BRIDGE_CODEX_FAILED
            else -> LibraryNoticeType.AI_REQUEST_FAILED
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

    private fun ManualSyncProgress.toUiProgress(): SyncProgress {
        return SyncProgress(
            phase = when (phase) {
                ManualSyncPhase.PREPARING -> SyncProgressPhase.PREPARING
                ManualSyncPhase.CHECKING_REMOTE -> SyncProgressPhase.CHECKING_REMOTE
                ManualSyncPhase.UPLOADING -> SyncProgressPhase.UPLOADING
                ManualSyncPhase.WRITING_MANIFEST -> SyncProgressPhase.WRITING_MANIFEST
                ManualSyncPhase.APPLYING_STATUS -> SyncProgressPhase.APPLYING_STATUS
            },
            current = current,
            total = total,
        )
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
