package com.foliore.android.viewmodel

import android.app.PendingIntent
import android.net.Uri
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.android.ai.AiCoverStyleRequest
import com.foliore.android.ai.AiGeneratedCoverOption
import com.foliore.android.ai.AiProviderMode
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.ai.OpenAiResult
import com.foliore.android.ai.isEnabled
import com.foliore.android.metadata.MetadataLookupResult
import com.foliore.android.metadata.MetadataLookupCandidate
import com.foliore.android.metadata.OpenMetadataLookupService
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
import com.foliore.android.storage.KindleEmailPreferenceRepository
import com.foliore.android.storage.LibraryDisplayPreferenceRepository
import com.foliore.android.storage.ManualSyncPhase
import com.foliore.android.storage.ManualSyncProgress
import com.foliore.android.storage.ManualSyncResult
import com.foliore.android.storage.MetadataPreferenceRepository
import com.foliore.android.storage.PreparedEpubResult
import com.foliore.android.storage.GoogleDriveConnectionResult
import com.foliore.android.storage.SyncProvider
import com.foliore.android.storage.SyncService
import com.foliore.android.storage.SyncWorkScheduler
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
    private val syncService: SyncService,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val aiPreferenceRepository: AiPreferenceRepository,
    private val aiCoverPromptRepository: AiCoverPromptRepository,
    private val kindleEmailPreferenceRepository: KindleEmailPreferenceRepository,
    private val libraryDisplayPreferenceRepository: LibraryDisplayPreferenceRepository,
    private val metadataPreferenceRepository: MetadataPreferenceRepository,
    private val openAiApiService: OpenAiApiService,
    private val openMetadataLookupService: OpenMetadataLookupService,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LibraryUiState(
            aiProviderMode = aiPreferenceRepository.providerMode.value,
            hasOpenAiApiKey = aiPreferenceRepository.hasOpenAiApiKey.value,
            hasGoogleBooksApiKey = metadataPreferenceRepository.hasGoogleBooksApiKey.value,
            aiCoverPromptRecords = aiCoverPromptRepository.records(),
            kindleEmailSettings = kindleEmailPreferenceRepository.settings,
            gridColumns = libraryDisplayPreferenceRepository.gridColumns().toBookGridColumns(),
            displayOptions = LibraryDisplayOptions(
                showTitle = libraryDisplayPreferenceRepository.showTitle(),
                showAuthor = libraryDisplayPreferenceRepository.showAuthor(),
                showSeries = libraryDisplayPreferenceRepository.showSeries(),
            ),
        ),
    )
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<LibraryNotice?>(null)
    val notice: StateFlow<LibraryNotice?> = _notice.asStateFlow()
    private var pendingGoogleDriveOperation: GoogleDriveOperation = GoogleDriveOperation.Connect

    init {
        refresh()
        refreshSyncState()
        syncWorkScheduler.refreshPeriodicSync()
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
            if (imported > 0) {
                enqueueAutoSync()
            }
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
        libraryDisplayPreferenceRepository.setGridColumns(gridColumns.name)
        _state.update { it.copy(gridColumns = gridColumns) }
    }

    fun setShowBookTitle(enabled: Boolean) {
        libraryDisplayPreferenceRepository.setShowTitle(enabled)
        _state.update { it.copy(displayOptions = it.displayOptions.copy(showTitle = enabled)) }
    }

    fun setShowBookAuthor(enabled: Boolean) {
        libraryDisplayPreferenceRepository.setShowAuthor(enabled)
        _state.update { it.copy(displayOptions = it.displayOptions.copy(showAuthor = enabled)) }
    }

    fun setShowBookSeries(enabled: Boolean) {
        libraryDisplayPreferenceRepository.setShowSeries(enabled)
        _state.update { it.copy(displayOptions = it.displayOptions.copy(showSeries = enabled)) }
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
            if (saved) {
                aiPreferenceRepository.setProviderMode(AiProviderMode.OPENAI_API)
            }
            _state.update {
                it.copy(
                    aiProviderMode = aiPreferenceRepository.providerMode.value,
                    hasOpenAiApiKey = aiPreferenceRepository.hasOpenAiApiKey.value,
                )
            }
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
        aiPreferenceRepository.setProviderMode(AiProviderMode.DISABLED)
        _state.update { it.copy(aiProviderMode = AiProviderMode.DISABLED, hasOpenAiApiKey = false) }
        _notice.value = LibraryNotice(LibraryNoticeType.AI_API_KEY_CLEARED)
    }

    fun saveGoogleBooksApiKey(apiKey: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                metadataPreferenceRepository.saveGoogleBooksApiKey(apiKey)
            }
            _state.update {
                it.copy(hasGoogleBooksApiKey = metadataPreferenceRepository.hasGoogleBooksApiKey.value)
            }
            _notice.value = LibraryNotice(
                if (saved) {
                    LibraryNoticeType.GOOGLE_BOOKS_API_KEY_SAVED
                } else {
                    LibraryNoticeType.GOOGLE_BOOKS_API_KEY_INVALID
                },
            )
        }
    }

    fun clearGoogleBooksApiKey() {
        metadataPreferenceRepository.clearGoogleBooksApiKey()
        _state.update { it.copy(hasGoogleBooksApiKey = false) }
        _notice.value = LibraryNotice(LibraryNoticeType.GOOGLE_BOOKS_API_KEY_CLEARED)
    }

    fun saveKindleEmail(email: String) {
        val saved = kindleEmailPreferenceRepository.saveKindleEmail(email)
        _state.update { it.copy(kindleEmailSettings = kindleEmailPreferenceRepository.settings) }
        _notice.value = LibraryNotice(
            if (saved) {
                LibraryNoticeType.KINDLE_EMAIL_SAVED
            } else {
                LibraryNoticeType.KINDLE_EMAIL_INVALID
            },
        )
    }

    fun clearKindleEmail() {
        kindleEmailPreferenceRepository.clearKindleEmail()
        _state.update { it.copy(kindleEmailSettings = kindleEmailPreferenceRepository.settings) }
        _notice.value = LibraryNotice(LibraryNoticeType.KINDLE_EMAIL_CLEARED)
    }

    fun saveKindleSmtpSettings(
        senderEmail: String,
        smtpHost: String,
        smtpPort: String,
        smtpUsername: String,
        smtpPassword: String,
    ) {
        val port = smtpPort.trim().toIntOrNull()
        val saved = if (port == null) {
            false
        } else {
            kindleEmailPreferenceRepository.saveSmtpSettings(
                senderEmail = senderEmail,
                smtpHost = smtpHost,
                smtpPort = port,
                smtpUsername = smtpUsername,
                smtpPassword = smtpPassword,
            )
        }
        _state.update { it.copy(kindleEmailSettings = kindleEmailPreferenceRepository.settings) }
        _notice.value = LibraryNotice(
            if (saved) {
                LibraryNoticeType.KINDLE_SMTP_SAVED
            } else {
                LibraryNoticeType.KINDLE_SMTP_INVALID
            },
        )
    }

    fun clearKindleSmtpSettings() {
        kindleEmailPreferenceRepository.clearSmtpSettings()
        _state.update { it.copy(kindleEmailSettings = kindleEmailPreferenceRepository.settings) }
        _notice.value = LibraryNotice(LibraryNoticeType.KINDLE_SMTP_CLEARED)
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

    fun lookupMetadataCandidates(
        bookId: String,
        draft: BookMetadataDraft,
        onCandidatesReady: (List<MetadataLookupCandidate>) -> Unit,
    ) {
        if (_state.value.hasActiveProgress) return

        viewModelScope.launch {
            _state.update {
                it.copy(importProgress = SyncProgress(SyncProgressPhase.LOOKING_UP_METADATA))
            }
            val book = withContext(Dispatchers.IO) {
                libraryRepository.getBook(bookId)
            }
            val result = if (book == null) {
                MetadataLookupResult.Failure("Book not found")
            } else {
                withContext(Dispatchers.IO) {
                    openMetadataLookupService.search(
                        book = book,
                        draft = draft,
                    )
                }
            }
            _state.update { it.copy(importProgress = null) }
            when (result) {
                is MetadataLookupResult.Success -> {
                    onCandidatesReady(result.candidates)
                    _notice.value = LibraryNotice(
                        if (result.candidates.isEmpty()) {
                            LibraryNoticeType.METADATA_LOOKUP_EMPTY
                        } else {
                            LibraryNoticeType.METADATA_LOOKUP_READY
                        },
                    )
                }
                is MetadataLookupResult.Failure -> {
                    _notice.value = LibraryNotice(LibraryNoticeType.METADATA_LOOKUP_FAILED)
                }
            }
        }
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
                            onProgress = { current, total ->
                                _state.update {
                                    it.copy(
                                        importProgress = SyncProgress(
                                            phase = SyncProgressPhase.GENERATING_COVERS,
                                            current = current,
                                            total = total,
                                        ),
                                    )
                                }
                            },
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
                    _notice.value = LibraryNotice(result.toAiFailureNoticeType())
                }
            }
        }
    }

    fun saveSelectedCoverPrompt(record: AiCoverPromptRecord) {
        val records = aiCoverPromptRepository.save(record)
        _state.update { it.copy(aiCoverPromptRecords = records) }
        enqueueAutoSync()
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

    fun useMetadataLookupCover(
        bookId: String,
        coverImagePath: String,
        onUpdated: () -> Unit = {},
    ) {
        replaceCover(
            bookId = bookId,
            imageUri = Uri.fromFile(File(coverImagePath)),
            onUpdated = onUpdated,
        )
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
                enqueueAutoSync()
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
            if (deletedCount > 0) {
                enqueueAutoSync()
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
                        enqueueAutoSync()
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
                        enqueueAutoSync()
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
                enqueueAutoSync()
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

    fun sendPreparedEpubToKindleEmail(
        bookId: String,
    ) {
        val credentials = kindleEmailPreferenceRepository.credentials()
        if (credentials == null) {
            _notice.value = LibraryNotice(LibraryNoticeType.KINDLE_EMAIL_MISSING)
            return
        }
        if (_state.value.hasActiveProgress) return

        _state.update {
            it.copy(importProgress = SyncProgress(SyncProgressPhase.SENDING_TO_KINDLE))
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                preparedEpubService.sendToKindleEmail(bookId, credentials)
            }
            refreshBooks()
            _state.update {
                if (it.importProgress?.phase == SyncProgressPhase.SENDING_TO_KINDLE) {
                    it.copy(importProgress = null)
                } else {
                    it
                }
            }
            when (result) {
                PreparedEpubResult.Sent -> _notice.value = LibraryNotice(LibraryNoticeType.KINDLE_EMAIL_SENT)
                else -> _notice.value = LibraryNotice(LibraryNoticeType.KINDLE_EMAIL_FAILED)
            }
        }
    }

    fun configureSyncFolder(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                syncService.configureManualFolder(uri)
            }
            val folderLabel = when (result) {
                is ManualSyncResult.Configured -> result.folderLabel
                else -> withContext(Dispatchers.IO) { syncService.syncLabel() }
            }
            _state.update {
                it.copy(
                    syncProvider = syncService.activeProvider(),
                    syncFolderLabel = folderLabel,
                    googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
                    isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
                    lastSyncSummary = null,
                )
            }
            _notice.value = LibraryNotice(
                when (result) {
                    is ManualSyncResult.Configured -> {
                        if (result.hasExistingLibrary) {
                            LibraryNoticeType.SYNC_EXISTING_LIBRARY_CONFIGURED
                        } else {
                            LibraryNoticeType.SYNC_FOLDER_CONFIGURED
                        }
                    }
                    else -> result.toNoticeType()
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
                syncService.syncNow { progress ->
                    _state.update { current ->
                        current.copy(syncProgress = progress.toUiProgress())
                    }
                }
            }
            val books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
            val folderLabel = withContext(Dispatchers.IO) {
                syncService.syncLabel()
            }
            val aiCoverPromptRecords = withContext(Dispatchers.IO) {
                aiCoverPromptRepository.records()
            }
            _state.update {
                it.withBooks(books).copy(
                    books = books,
                    isSyncing = false,
                    syncProgress = null,
                    syncProvider = syncService.activeProvider(),
                    syncFolderLabel = folderLabel,
                    googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
                    isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
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
                syncService.disconnectActiveProvider()
            }
            _state.update {
                it.copy(
                    syncProvider = syncService.activeProvider(),
                    syncFolderLabel = syncService.syncLabel(),
                    googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
                    isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
                    isSyncing = false,
                    syncProgress = null,
                )
            }
            _notice.value = LibraryNotice(LibraryNoticeType.SYNC_DISCONNECTED)
        }
    }

    fun connectGoogleDrive(onResolutionRequired: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            pendingGoogleDriveOperation = GoogleDriveOperation.Connect
            val result = withContext(Dispatchers.IO) {
                syncService.beginGoogleDriveConnection()
            }
            handleGoogleDriveConnectionResult(result, onResolutionRequired)
        }
    }

    fun completeGoogleDriveAuthorization(
        data: Intent?,
        onResolutionRequired: (PendingIntent) -> Unit,
    ) {
        viewModelScope.launch {
            val result = when (pendingGoogleDriveOperation) {
                GoogleDriveOperation.Connect -> withContext(Dispatchers.IO) {
                    syncService.completeGoogleDriveConnection(data)
                }
                GoogleDriveOperation.DeleteBackup -> withContext(Dispatchers.IO) {
                    when (val completed = syncService.completeGoogleDriveConnection(data)) {
                        GoogleDriveConnectionResult.Connected -> syncService.deleteGoogleDriveBackup()
                        else -> completed
                    }
                }
            }
            handleGoogleDriveConnectionResult(result, onResolutionRequired)
        }
    }

    fun deleteGoogleDriveBackup(onResolutionRequired: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            pendingGoogleDriveOperation = GoogleDriveOperation.DeleteBackup
            val result = withContext(Dispatchers.IO) {
                syncService.deleteGoogleDriveBackup()
            }
            handleGoogleDriveConnectionResult(result, onResolutionRequired)
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        syncService.setAutoSyncEnabled(enabled)
        syncWorkScheduler.refreshPeriodicSync()
        _state.update { it.copy(isAutoSyncEnabled = syncService.autoSyncEnabled()) }
        _notice.value = LibraryNotice(LibraryNoticeType.SYNC_AUTO_SETTINGS_UPDATED)
    }

    fun setAutoSyncWifiOnly(enabled: Boolean) {
        syncService.setAutoSyncWifiOnly(enabled)
        syncWorkScheduler.refreshPeriodicSync()
        _state.update { it.copy(isAutoSyncWifiOnly = syncService.autoSyncWifiOnly()) }
        _notice.value = LibraryNotice(LibraryNoticeType.SYNC_AUTO_SETTINGS_UPDATED)
    }

    fun consumeNotice() {
        _notice.value = null
    }

    private fun handleGoogleDriveConnectionResult(
        result: GoogleDriveConnectionResult,
        onResolutionRequired: (PendingIntent) -> Unit,
    ) {
        when (result) {
            GoogleDriveConnectionResult.Connected -> {
                refreshSyncState()
                _notice.value = LibraryNotice(LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONNECTED)
            }
            GoogleDriveConnectionResult.DeletedBackup -> {
                _notice.value = LibraryNotice(LibraryNoticeType.SYNC_GOOGLE_DRIVE_BACKUP_DELETED)
            }
            GoogleDriveConnectionResult.Failed -> {
                _notice.value = LibraryNotice(LibraryNoticeType.SYNC_GOOGLE_DRIVE_UNAVAILABLE)
            }
            is GoogleDriveConnectionResult.NeedsResolution -> {
                onResolutionRequired(result.pendingIntent)
            }
            is GoogleDriveConnectionResult.SyncFailed -> {
                _notice.value = LibraryNotice(result.result.toNoticeType())
            }
        }
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

    private fun refreshSyncState() {
        viewModelScope.launch {
            val folderLabel = withContext(Dispatchers.IO) {
                syncService.syncLabel()
            }
            _state.update {
                it.copy(
                    syncProvider = syncService.activeProvider(),
                    syncFolderLabel = folderLabel,
                    googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
                    isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
                    isAutoSyncEnabled = syncService.autoSyncEnabled(),
                    isAutoSyncWifiOnly = syncService.autoSyncWifiOnly(),
                )
            }
        }
    }

    private fun enqueueAutoSync() {
        syncWorkScheduler.enqueueAutoSync()
    }

    private fun hasUsableOpenAiApiProvider(): Boolean {
        val current = _state.value
        return current.aiProviderMode == AiProviderMode.OPENAI_API && current.hasOpenAiApiKey
    }

    private fun hasUsableAiMetadataProvider(): Boolean {
        return hasUsableOpenAiApiProvider()
    }

    private fun hasUsableAiCoverProvider(): Boolean {
        return hasUsableOpenAiApiProvider()
    }

    private fun OpenAiResult.Failure.toAiFailureNoticeType(): LibraryNoticeType {
        return when (code) {
            "provider_disabled" -> LibraryNoticeType.AI_PROVIDER_NOT_READY
            else -> LibraryNoticeType.AI_REQUEST_FAILED
        }
    }

    private fun ManualSyncResult.toNoticeType(): LibraryNoticeType {
        return when (this) {
            ManualSyncResult.NotConfigured -> LibraryNoticeType.SYNC_NOT_CONFIGURED
            ManualSyncResult.FolderUnavailable -> LibraryNoticeType.SYNC_FOLDER_UNAVAILABLE
            ManualSyncResult.FolderLooksLikeLibrarySubfolder -> LibraryNoticeType.SYNC_FOLDER_LOOKS_LIKE_SUBFOLDER
            ManualSyncResult.GoogleDriveAuthorizationRequired -> LibraryNoticeType.SYNC_GOOGLE_DRIVE_AUTH_REQUIRED
            ManualSyncResult.GoogleDriveQuotaExceeded -> LibraryNoticeType.SYNC_GOOGLE_DRIVE_QUOTA_EXCEEDED
            ManualSyncResult.GoogleDriveUnavailable -> LibraryNoticeType.SYNC_GOOGLE_DRIVE_UNAVAILABLE
            ManualSyncResult.Failed -> LibraryNoticeType.SYNC_FAILED
            is ManualSyncResult.Configured -> {
                if (hasExistingLibrary) {
                    LibraryNoticeType.SYNC_EXISTING_LIBRARY_CONFIGURED
                } else {
                    LibraryNoticeType.SYNC_FOLDER_CONFIGURED
                }
            }
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
                ManualSyncPhase.DOWNLOADING -> SyncProgressPhase.DOWNLOADING
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

private fun String?.toBookGridColumns(): BookGridColumns {
    return this?.let { raw ->
        BookGridColumns.values().firstOrNull { it.name == raw }
    } ?: BookGridColumns.AUTO
}

private enum class GoogleDriveOperation {
    Connect,
    DeleteBackup,
}
