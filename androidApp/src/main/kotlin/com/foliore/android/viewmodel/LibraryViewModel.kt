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
import com.foliore.android.storage.PendingEpubImport
import com.foliore.android.storage.PreparedEpubResult
import com.foliore.android.storage.GoogleDriveBackupStats
import com.foliore.android.storage.GoogleDriveBackupStatsResult
import com.foliore.android.storage.GoogleDriveAuthorizationFailure
import com.foliore.android.storage.GoogleDriveConnectionResult
import com.foliore.android.storage.GoogleDriveRemoteLibraryRecovery
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
    private val billingRepository: com.foliore.android.billing.PlayBillingRepository,
    private val libraryRepository: LibraryRepository,
    private val epubImporter: AndroidEpubImporter,
    private val bookEditor: AndroidBookEditor,
    private val bookDeleter: AndroidBookDeleter,
    private val preparedEpubService: AndroidPreparedEpubService,
    private val originalFilesService: com.foliore.android.storage.AndroidOriginalFilesService,
    private val syncService: SyncService,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val aiPreferenceRepository: AiPreferenceRepository,
    private val aiCoverPromptRepository: AiCoverPromptRepository,
    private val kindleEmailPreferenceRepository: KindleEmailPreferenceRepository,
    private val libraryDisplayPreferenceRepository: LibraryDisplayPreferenceRepository,
    private val openAiApiService: OpenAiApiService,
    private val openMetadataLookupService: OpenMetadataLookupService,
) : ViewModel() {
    private val initialSyncProvider = syncService.activeProvider()
    private val _state = MutableStateFlow(
        LibraryUiState(
            syncProvider = initialSyncProvider,
            googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
            isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
            googleDriveBackupStats = if (initialSyncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                syncService.cachedGoogleDriveBackupStats()
            } else {
                null
            },
            googleDriveRemoteLibraryRecovery =
                if (initialSyncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                    syncService.pendingGoogleDriveRemoteLibraryRecovery()
                } else {
                    null
                },
            isAutoSyncEnabled = syncService.autoSyncEnabled(),
            isAutoSyncWifiOnly = syncService.autoSyncWifiOnly(),
            aiProviderMode = aiPreferenceRepository.providerMode.value,
            hasOpenAiApiKey = aiPreferenceRepository.hasOpenAiApiKey.value,
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
    private val pendingEpubImports = linkedMapOf<String, PendingEpubImport>()
    private var limitedImportUris: List<Uri> = emptyList()
    val billingState = billingRepository.state

    fun refreshPurchases() = billingRepository.refresh()
    fun restorePurchase() = billingRepository.refresh(restore = true)
    fun purchaseUnlimited(activity: android.app.Activity) = billingRepository.purchase(activity)

    private var pendingImportCounts = ImportCounts()
    private var isResolvingImportConflicts = false
    private var pendingGoogleDriveOperation: GoogleDriveOperation = GoogleDriveOperation.Connect
    private val attemptedCoverRecoveries = mutableSetOf<String>()

    init {
        refresh()
        refreshSyncState()
        syncWorkScheduler.refreshPeriodicSync()
    }

    fun refresh() {
        viewModelScope.launch {
            val (books, recoveredCover) = loadBooksAndRecoverMissingCovers()
            _state.update { it.withBooks(books) }
            if (recoveredCover) enqueueAutoSync()
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty() || pendingEpubImports.isNotEmpty() || _state.value.isImporting || limitedImportUris.isNotEmpty()) return
        val available = com.foliore.android.billing.LibraryLimit.remaining(_state.value.books.size, billingState.value.unlimited)
        if (uris.size > available) {
            limitedImportUris = uris
            _state.update { it.copy(importLimitAvailable = available) }
        } else startImport(uris)
    }

    fun continueLimitedImport() {
        val uris = limitedImportUris
        limitedImportUris = emptyList()
        _state.update { it.copy(importLimitAvailable = null) }
        startImport(uris)
    }

    fun dismissImportLimit() {
        limitedImportUris = emptyList()
        _state.update { it.copy(importLimitAvailable = null, importLimitSkipped = 0) }
    }

    private fun startImport(uris: List<Uri>) {
        if (uris.isEmpty() || pendingEpubImports.isNotEmpty() || _state.value.isImporting) return

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

            var counts = ImportCounts()

            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    when (val importResult = epubImporter.import(uri)) {
                        is EpubImportResult.Imported -> counts = counts.copy(imported = counts.imported + 1)
                        is EpubImportResult.Duplicate -> counts = counts.copy(duplicates = counts.duplicates + 1)
                        EpubImportResult.LimitReached -> counts = counts.copy(limited = counts.limited + 1)
                        is EpubImportResult.Failed -> counts = counts.copy(
                            failed = counts.failed + 1,
                            drmFailed = counts.drmFailed || importResult.reason == com.foliore.android.storage.ImportFailureReason.DRM_PROTECTED,
                            conversionFailed = counts.conversionFailed || importResult.reason == com.foliore.android.storage.ImportFailureReason.CONVERSION_FAILED,
                            tooLarge = counts.tooLarge || importResult.reason == com.foliore.android.storage.ImportFailureReason.TOO_LARGE,
                        )
                        is EpubImportResult.NeedsReview -> {
                            val pendingImport = importResult.pendingImport
                            val duplicatePending = pendingEpubImports.values.any {
                                it.contentFingerprint == pendingImport.contentFingerprint
                            }
                            if (duplicatePending) {
                                epubImporter.discard(pendingImport)
                                counts = counts.copy(duplicates = counts.duplicates + 1)
                            } else {
                                pendingEpubImports[pendingImport.token] = pendingImport
                            }
                        }
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
            pendingImportCounts = counts
            _state.update {
                it.withBooks(books).copy(
                    isImporting = false,
                    importProgress = null,
                    pendingImportConflicts = pendingEpubImports.values.map { pending ->
                        pending.toUiModel()
                    },
                )
            }
            if (counts.imported > 0) {
                enqueueAutoSync()
            }
            if (pendingEpubImports.isEmpty()) publishImportNotice(counts)
        }
    }

    fun resolveImportConflicts(choices: Map<String, ImportDuplicateChoice>) {
        if (
            pendingEpubImports.isEmpty() ||
            _state.value.isImporting ||
            isResolvingImportConflicts
        ) {
            return
        }

        isResolvingImportConflicts = true
        val importsToResolve = pendingEpubImports.values.toList()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    pendingImportConflicts = emptyList(),
                    importProgress = SyncProgress(
                        phase = SyncProgressPhase.IMPORTING,
                        current = 0,
                        total = importsToResolve.size,
                    ),
                )
            }

            var counts = pendingImportCounts
            withContext(Dispatchers.IO) {
                importsToResolve.forEachIndexed { index, pendingImport ->
                    when (choices[pendingImport.token] ?: ImportDuplicateChoice.KEEP_LIBRARY) {
                        ImportDuplicateChoice.KEEP_LIBRARY -> {
                            epubImporter.discard(pendingImport)
                            counts = counts.copy(duplicates = counts.duplicates + 1)
                        }
                        ImportDuplicateChoice.USE_IMPORTED -> {
                            when (epubImporter.commit(pendingImport)) {
                                is EpubImportResult.Imported -> {
                                    bookDeleter.delete(pendingImport.existingBookId)
                                    counts = counts.copy(imported = counts.imported + 1)
                                }
                                is EpubImportResult.Duplicate -> {
                                    counts = counts.copy(duplicates = counts.duplicates + 1)
                                }
                                EpubImportResult.LimitReached -> counts = counts.copy(limited = counts.limited + 1)
                                is EpubImportResult.Failed,
                                is EpubImportResult.NeedsReview -> {
                                    counts = counts.copy(failed = counts.failed + 1)
                                }
                            }
                        }
                    }
                    _state.update {
                        it.copy(
                            importProgress = SyncProgress(
                                phase = SyncProgressPhase.IMPORTING,
                                current = index + 1,
                                total = importsToResolve.size,
                            ),
                        )
                    }
                }
            }

            pendingImportCounts = ImportCounts()
            pendingEpubImports.clear()
            isResolvingImportConflicts = false
            val books = withContext(Dispatchers.IO) { libraryRepository.getBooks() }
            _state.update {
                it.withBooks(books).copy(
                    isImporting = false,
                    importProgress = null,
                    pendingImportConflicts = emptyList(),
                )
            }
            if (counts.imported > 0) enqueueAutoSync()
            publishImportNotice(counts)
        }
    }

    fun dismissImportConflicts() {
        resolveImportConflicts(emptyMap())
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
        if (_state.value.hasActiveProgress) { onCandidatesReady(emptyList()); return }

        viewModelScope.launch {
            _state.update {
                it.copy(importProgress = SyncProgress(SyncProgressPhase.LOOKING_UP_METADATA))
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    val book = libraryRepository.getBook(bookId)
                    if (book == null) MetadataLookupResult.Failure("Book not found")
                    else openMetadataLookupService.search(book, draft)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                MetadataLookupResult.Failure("Metadata provider unavailable")
            } finally {
                _state.update { it.copy(importProgress = null) }
            }
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
                    onCandidatesReady(emptyList())
                    _notice.value = LibraryNotice(LibraryNoticeType.METADATA_LOOKUP_FAILED)
                }
            }
        }
    }

    fun resolveMetadataCandidate(
        bookId: String,
        candidate: MetadataLookupCandidate,
        onReady: (MetadataLookupCandidate) -> Unit,
    ) {
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                runCatching {
                    libraryRepository.getBook(bookId)?.let { openMetadataLookupService.resolve(it, candidate) }
                }.getOrNull() ?: candidate.copy(detailsResolved = true)
            }
            onReady(resolved)
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

    suspend fun originalFiles(bookId: String) = withContext(Dispatchers.IO) { originalFilesService.list(bookId) }

    suspend fun shareOriginalFile(bookId: String, fileId: String) = withContext(Dispatchers.IO) {
        originalFilesService.share(bookId, fileId)
    }

    suspend fun saveOriginalFile(bookId: String, fileId: String, destination: Uri) = withContext(Dispatchers.IO) {
        originalFilesService.save(bookId, fileId, destination)
    }

    suspend fun deleteOriginalFile(bookId: String, fileId: String) = withContext(Dispatchers.IO) {
        originalFilesService.delete(bookId, fileId)
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
                    googleDriveBackupStats = null,
                    isGoogleDriveBackupStatsLoading = false,
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
        performSync(allowGoogleDriveLibraryRecreation = false)
    }

    fun recreateGoogleDriveLibrary() {
        performSync(allowGoogleDriveLibraryRecreation = true)
    }

    fun dismissGoogleDriveRemoteLibraryRecovery() {
        _state.update { it.copy(googleDriveRemoteLibraryRecovery = null) }
    }

    private fun performSync(allowGoogleDriveLibraryRecreation: Boolean) {
        if (_state.value.hasActiveProgress || _state.value.isGoogleDriveBackupStatsLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncProgress = SyncProgress(SyncProgressPhase.PREPARING),
                )
            }
            val result = withContext(Dispatchers.IO) {
                syncService.syncNow(
                    allowGoogleDriveLibraryRecreation = allowGoogleDriveLibraryRecreation,
                ) { progress ->
                    val committedBook = progress.committedBookId?.let { bookId ->
                        libraryRepository.getBook(bookId)
                    }
                    val refreshedBooks = if (
                        progress.committedBookId != null && committedBook == null
                    ) {
                        libraryRepository.getBooks()
                    } else {
                        null
                    }
                    _state.update { current ->
                        val books = refreshedBooks ?: current.books.withCommittedSyncBook(
                            committedBook = committedBook,
                            removedLocalBookId = progress.removedLocalBookId,
                        )
                        current.withBooks(books).copy(syncProgress = progress.toUiProgress())
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
                    googleDriveRemoteLibraryRecovery = when (result) {
                        is ManualSyncResult.GoogleDriveRemoteLibraryRemoved -> {
                            syncService.pendingGoogleDriveRemoteLibraryRecovery()
                        }
                        is ManualSyncResult.Synced -> null
                        else -> it.googleDriveRemoteLibraryRecovery
                    },
                    aiCoverPromptRecords = aiCoverPromptRecords,
                    googleDriveBackupStats = it.googleDriveBackupStats,
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
            if (result !is ManualSyncResult.GoogleDriveRemoteLibraryRemoved) {
                _notice.value = LibraryNotice(result.toNoticeType())
            }
            if (
                result is ManualSyncResult.Synced &&
                syncService.activeProvider() == SyncProvider.GOOGLE_DRIVE_APPDATA
            ) {
                refreshGoogleDriveBackupStatsSilently()
            }
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
                    googleDriveBackupStats = null,
                    googleDriveRemoteLibraryRecovery = null,
                    isGoogleDriveBackupStatsLoading = false,
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
            when (pendingGoogleDriveOperation) {
                GoogleDriveOperation.Connect -> {
                    val result = withContext(Dispatchers.IO) {
                        syncService.completeGoogleDriveConnection(data)
                    }
                    handleGoogleDriveConnectionResult(result, onResolutionRequired)
                }
                GoogleDriveOperation.DeleteBackup -> {
                    beginGoogleDriveDeletionProgress()
                    val result = withContext(Dispatchers.IO) {
                        when (val completed = syncService.completeGoogleDriveConnection(data)) {
                            GoogleDriveConnectionResult.Connected -> {
                                syncService.deleteGoogleDriveBackup(::updateGoogleDriveDeletionProgress)
                            }
                            else -> completed
                        }
                    }
                    handleGoogleDriveConnectionResult(result, onResolutionRequired)
                }
                GoogleDriveOperation.DeleteBackupAndDisconnect -> {
                    beginGoogleDriveDeletionProgress()
                    val result = withContext(Dispatchers.IO) {
                        when (val completed = syncService.completeGoogleDriveConnection(data)) {
                            GoogleDriveConnectionResult.Connected -> {
                                syncService.deleteGoogleDriveBackup(::updateGoogleDriveDeletionProgress)
                            }
                            else -> completed
                        }
                    }
                    handleGoogleDriveCleanupAndDisconnectResult(result, onResolutionRequired)
                }
                GoogleDriveOperation.LoadBackupStats -> {
                    _state.update { it.copy(isGoogleDriveBackupStatsLoading = true) }
                    val result = withContext(Dispatchers.IO) {
                        when (val completed = syncService.completeGoogleDriveConnection(data)) {
                            GoogleDriveConnectionResult.Connected -> syncService.googleDriveBackupStats()
                            GoogleDriveConnectionResult.DeletedBackup -> {
                                GoogleDriveBackupStatsResult.Loaded(GoogleDriveBackupStats.Deleted)
                            }
                            is GoogleDriveConnectionResult.Failed -> {
                                GoogleDriveBackupStatsResult.SyncFailed(
                                    when (completed.reason) {
                                        GoogleDriveAuthorizationFailure.CONFIGURATION_REQUIRED -> {
                                            ManualSyncResult.GoogleDriveConfigurationRequired
                                        }
                                        GoogleDriveAuthorizationFailure.CANCELLED -> {
                                            ManualSyncResult.GoogleDriveAuthorizationRequired
                                        }
                                        GoogleDriveAuthorizationFailure.UNAVAILABLE -> {
                                            ManualSyncResult.GoogleDriveUnavailable
                                        }
                                    },
                                )
                            }
                            is GoogleDriveConnectionResult.NeedsResolution -> {
                                GoogleDriveBackupStatsResult.NeedsResolution(completed.pendingIntent)
                            }
                            is GoogleDriveConnectionResult.SyncFailed -> {
                                GoogleDriveBackupStatsResult.SyncFailed(completed.result)
                            }
                        }
                    }
                    handleGoogleDriveBackupStatsResult(result, onResolutionRequired)
                }
            }
        }
    }

    fun deleteGoogleDriveBackup(onResolutionRequired: (PendingIntent) -> Unit) {
        if (_state.value.isGoogleDriveBackupStatsLoading || _state.value.hasActiveProgress) return

        pendingGoogleDriveOperation = GoogleDriveOperation.DeleteBackup
        beginGoogleDriveDeletionProgress()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                syncService.deleteGoogleDriveBackup(::updateGoogleDriveDeletionProgress)
            }
            handleGoogleDriveConnectionResult(result, onResolutionRequired)
        }
    }

    fun clearGoogleDriveAndDisconnect(onResolutionRequired: (PendingIntent) -> Unit) {
        if (_state.value.isGoogleDriveBackupStatsLoading || _state.value.hasActiveProgress) return

        pendingGoogleDriveOperation = GoogleDriveOperation.DeleteBackupAndDisconnect
        beginGoogleDriveDeletionProgress()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                syncService.deleteGoogleDriveBackup(::updateGoogleDriveDeletionProgress)
            }
            handleGoogleDriveCleanupAndDisconnectResult(result, onResolutionRequired)
        }
    }

    fun refreshGoogleDriveBackupStats(onResolutionRequired: (PendingIntent) -> Unit) {
        if (_state.value.isGoogleDriveBackupStatsLoading) return

        pendingGoogleDriveOperation = GoogleDriveOperation.LoadBackupStats
        _state.update { it.copy(isGoogleDriveBackupStatsLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                syncService.googleDriveBackupStats()
            }
            handleGoogleDriveBackupStatsResult(result, onResolutionRequired)
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

    private fun beginGoogleDriveDeletionProgress() {
        _state.update {
            it.copy(
                isSyncing = true,
                syncProgress = SyncProgress(SyncProgressPhase.DELETING_REMOTE),
                isGoogleDriveBackupStatsLoading = true,
            )
        }
    }

    private fun updateGoogleDriveDeletionProgress(progress: ManualSyncProgress) {
        _state.update {
            it.copy(
                isSyncing = true,
                syncProgress = progress.toUiProgress(),
                isGoogleDriveBackupStatsLoading = true,
            )
        }
    }

    private fun handleGoogleDriveConnectionResult(
        result: GoogleDriveConnectionResult,
        onResolutionRequired: (PendingIntent) -> Unit,
    ) {
        when (result) {
            GoogleDriveConnectionResult.Connected -> {
                _state.update {
                    it.copy(
                        googleDriveBackupStats = syncService.cachedGoogleDriveBackupStats()
                            ?: it.googleDriveBackupStats,
                        googleDriveRemoteLibraryRecovery =
                            syncService.pendingGoogleDriveRemoteLibraryRecovery(),
                        isGoogleDriveBackupStatsLoading = false,
                        isSyncing = false,
                        syncProgress = null,
                    )
                }
                refreshSyncState()
                _notice.value = LibraryNotice(LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONNECTED)
                refreshGoogleDriveBackupStatsSilently()
            }
            GoogleDriveConnectionResult.DeletedBackup -> {
                _state.update {
                    it.copy(
                        googleDriveBackupStats = GoogleDriveBackupStats.Deleted,
                        googleDriveRemoteLibraryRecovery =
                            syncService.pendingGoogleDriveRemoteLibraryRecovery(),
                        isGoogleDriveBackupStatsLoading = false,
                        isSyncing = false,
                        syncProgress = null,
                        lastSyncSummary = null,
                    )
                }
                _notice.value = LibraryNotice(LibraryNoticeType.SYNC_GOOGLE_DRIVE_BACKUP_DELETED)
            }
            is GoogleDriveConnectionResult.Failed -> {
                _state.update {
                    it.copy(
                        isGoogleDriveBackupStatsLoading = false,
                        isSyncing = false,
                        syncProgress = null,
                    )
                }
                _notice.value = LibraryNotice(
                    when (result.reason) {
                        GoogleDriveAuthorizationFailure.CONFIGURATION_REQUIRED -> {
                            LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONFIGURATION_REQUIRED
                        }
                        GoogleDriveAuthorizationFailure.CANCELLED -> {
                            LibraryNoticeType.SYNC_GOOGLE_DRIVE_AUTH_REQUIRED
                        }
                        GoogleDriveAuthorizationFailure.UNAVAILABLE -> {
                            LibraryNoticeType.SYNC_GOOGLE_DRIVE_UNAVAILABLE
                        }
                    },
                )
            }
            is GoogleDriveConnectionResult.NeedsResolution -> {
                _state.update {
                    it.copy(
                        isGoogleDriveBackupStatsLoading = false,
                        isSyncing = false,
                        syncProgress = null,
                    )
                }
                onResolutionRequired(result.pendingIntent)
            }
            is GoogleDriveConnectionResult.SyncFailed -> {
                _state.update {
                    it.copy(
                        isGoogleDriveBackupStatsLoading = false,
                        isSyncing = false,
                        syncProgress = null,
                    )
                }
                _notice.value = LibraryNotice(result.result.toNoticeType())
            }
        }
    }

    private suspend fun handleGoogleDriveCleanupAndDisconnectResult(
        result: GoogleDriveConnectionResult,
        onResolutionRequired: (PendingIntent) -> Unit,
    ) {
        if (result != GoogleDriveConnectionResult.DeletedBackup) {
            handleGoogleDriveConnectionResult(result, onResolutionRequired)
            return
        }

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
                googleDriveBackupStats = null,
                googleDriveRemoteLibraryRecovery = null,
                isGoogleDriveBackupStatsLoading = false,
                lastSyncSummary = null,
            )
        }
        _notice.value = LibraryNotice(LibraryNoticeType.SYNC_DISCONNECTED)
    }

    private fun handleGoogleDriveBackupStatsResult(
        result: GoogleDriveBackupStatsResult,
        onResolutionRequired: (PendingIntent) -> Unit,
    ) {
        when (result) {
            is GoogleDriveBackupStatsResult.Loaded -> {
                _state.update {
                    it.copy(
                        googleDriveBackupStats = result.stats,
                        googleDriveRemoteLibraryRecovery =
                            syncService.pendingGoogleDriveRemoteLibraryRecovery(),
                        isGoogleDriveBackupStatsLoading = false,
                    )
                }
            }
            is GoogleDriveBackupStatsResult.NeedsResolution -> {
                _state.update { it.copy(isGoogleDriveBackupStatsLoading = false) }
                onResolutionRequired(result.pendingIntent)
            }
            is GoogleDriveBackupStatsResult.SyncFailed -> {
                _state.update { it.copy(isGoogleDriveBackupStatsLoading = false) }
                _notice.value = LibraryNotice(result.result.toNoticeType())
            }
        }
    }

    fun refreshGoogleDriveBackupStatsSilently() {
        if (_state.value.isGoogleDriveBackupStatsLoading) return

        _state.update { it.copy(isGoogleDriveBackupStatsLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                syncService.googleDriveBackupStats()
            }
            when (result) {
                is GoogleDriveBackupStatsResult.Loaded -> {
                    _state.update {
                        it.copy(
                            googleDriveBackupStats = result.stats,
                            googleDriveRemoteLibraryRecovery =
                                syncService.pendingGoogleDriveRemoteLibraryRecovery(),
                            isGoogleDriveBackupStatsLoading = false,
                        )
                    }
                }
                is GoogleDriveBackupStatsResult.NeedsResolution,
                is GoogleDriveBackupStatsResult.SyncFailed -> {
                    _state.update { it.copy(isGoogleDriveBackupStatsLoading = false) }
                }
            }
        }
    }

    private suspend fun refreshBooks(clearBatchSelection: Boolean = false) {
        val (books, recoveredCover) = loadBooksAndRecoverMissingCovers()
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
        if (recoveredCover) enqueueAutoSync()
    }

    private suspend fun loadBooksAndRecoverMissingCovers(): Pair<List<LibraryBook>, Boolean> {
        var books = withContext(Dispatchers.IO) {
            libraryRepository.getBooks()
        }
        val candidates = books.filter { libraryBook ->
            !bookEditor.hasCompleteReadableCover(libraryBook) &&
                attemptedCoverRecoveries.add(libraryBook.book.id)
        }
        if (candidates.isEmpty()) return books to false

        val recovered = withContext(Dispatchers.IO) {
            candidates.count { candidate ->
                bookEditor.recoverMissingCover(candidate.book.id) is BookEditResult.Success
            }
        }
        if (recovered > 0) {
            books = withContext(Dispatchers.IO) {
                libraryRepository.getBooks()
            }
        }
        return books to (recovered > 0)
    }

    private fun refreshSyncState() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val provider = syncService.activeProvider()
                SyncStateSnapshot(
                    provider = provider,
                    label = syncService.syncLabel(),
                    googleDriveAccountLabel = syncService.googleDriveAccountLabel(),
                    isGoogleDriveConnected = syncService.isGoogleDriveConnected(),
                    isAutoSyncEnabled = syncService.autoSyncEnabled(),
                    isAutoSyncWifiOnly = syncService.autoSyncWifiOnly(),
                    googleDriveBackupStats = if (provider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                        syncService.cachedGoogleDriveBackupStats()
                    } else {
                        null
                    },
                    googleDriveRemoteLibraryRecovery =
                        if (provider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                            syncService.pendingGoogleDriveRemoteLibraryRecovery()
                        } else {
                            null
                        },
                )
            }
            _state.update {
                it.copy(
                    syncProvider = snapshot.provider,
                    syncFolderLabel = snapshot.label,
                    googleDriveAccountLabel = snapshot.googleDriveAccountLabel,
                    isGoogleDriveConnected = snapshot.isGoogleDriveConnected,
                    isAutoSyncEnabled = snapshot.isAutoSyncEnabled,
                    isAutoSyncWifiOnly = snapshot.isAutoSyncWifiOnly,
                    googleDriveBackupStats = if (snapshot.provider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                        snapshot.googleDriveBackupStats ?: it.googleDriveBackupStats
                    } else {
                        null
                    },
                    googleDriveRemoteLibraryRecovery = snapshot.googleDriveRemoteLibraryRecovery,
                )
            }
        }
    }

    private fun enqueueAutoSync() {
        syncWorkScheduler.enqueueAutoSync()
    }

    private fun PendingEpubImport.toUiModel(): ImportDuplicateConflict {
        return ImportDuplicateConflict(
            token = token,
            existingTitle = existingTitle,
            existingAuthors = existingAuthors,
            incomingTitle = incomingTitle,
            incomingAuthors = incomingAuthors,
        )
    }

    private fun publishImportNotice(counts: ImportCounts) {
        if (counts.limited > 0) {
            _state.update { it.copy(importLimitSkipped = counts.limited) }
            if (counts.imported == 0 && counts.duplicates == 0 && counts.failed == 0) return
        }
        _notice.value = LibraryNotice(
            type = when {
                counts.drmFailed -> LibraryNoticeType.IMPORT_DRM_PROTECTED
                counts.tooLarge -> LibraryNoticeType.IMPORT_TOO_LARGE
                counts.conversionFailed -> LibraryNoticeType.IMPORT_CONVERSION_FAILED
                counts.imported > 0 && counts.duplicates == 0 && counts.failed == 0 -> {
                    LibraryNoticeType.IMPORTED
                }
                counts.imported == 0 && counts.duplicates > 0 && counts.failed == 0 -> {
                    LibraryNoticeType.DUPLICATE
                }
                counts.imported == 0 && counts.failed > 0 && counts.duplicates == 0 -> {
                    LibraryNoticeType.FAILED
                }
                else -> LibraryNoticeType.MIXED
            },
        )
    }

    override fun onCleared() {
        pendingEpubImports.values.forEach(epubImporter::discard)
        pendingEpubImports.clear()
        isResolvingImportConflicts = false
        super.onCleared()
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
            ManualSyncResult.GoogleDriveConfigurationRequired -> {
                LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONFIGURATION_REQUIRED
            }
            ManualSyncResult.GoogleDriveQuotaExceeded -> LibraryNoticeType.SYNC_GOOGLE_DRIVE_QUOTA_EXCEEDED
            ManualSyncResult.GoogleDriveUnavailable -> LibraryNoticeType.SYNC_GOOGLE_DRIVE_UNAVAILABLE
            is ManualSyncResult.GoogleDriveRemoteLibraryRemoved -> LibraryNoticeType.SYNC_FAILED
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
                ManualSyncPhase.DELETING_REMOTE -> SyncProgressPhase.DELETING_REMOTE
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

    private fun List<LibraryBook>.withCommittedSyncBook(
        committedBook: LibraryBook?,
        removedLocalBookId: String?,
    ): List<LibraryBook> {
        if (committedBook == null && removedLocalBookId == null) return this

        val retainedBooks = filterNot { currentBook ->
            currentBook.book.id == removedLocalBookId ||
                currentBook.book.id == committedBook?.book?.id
        }
        return if (committedBook != null) {
            listOf(committedBook) + retainedBooks
        } else {
            retainedBooks
        }
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
    DeleteBackupAndDisconnect,
    LoadBackupStats,
}

private data class ImportCounts(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val limited: Int = 0,
    val drmFailed: Boolean = false,
    val conversionFailed: Boolean = false,
    val tooLarge: Boolean = false,
)

private data class SyncStateSnapshot(
    val provider: SyncProvider,
    val label: String?,
    val googleDriveAccountLabel: String?,
    val isGoogleDriveConnected: Boolean,
    val isAutoSyncEnabled: Boolean,
    val isAutoSyncWifiOnly: Boolean,
    val googleDriveBackupStats: GoogleDriveBackupStats?,
    val googleDriveRemoteLibraryRecovery: GoogleDriveRemoteLibraryRecovery?,
)
