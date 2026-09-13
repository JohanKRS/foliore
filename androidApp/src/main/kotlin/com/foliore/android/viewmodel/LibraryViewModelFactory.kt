package com.foliore.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.metadata.OpenMetadataLookupService
import com.foliore.android.storage.AiCoverPromptRepository
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.KindleEmailPreferenceRepository
import com.foliore.android.storage.LibraryDisplayPreferenceRepository
import com.foliore.android.storage.SyncService
import com.foliore.android.storage.SyncWorkScheduler
import com.foliore.shared.library.LibraryRepository

class LibraryViewModelFactory(
    private val billingRepository: com.foliore.android.billing.PlayBillingRepository,
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
    private val openAiApiService: OpenAiApiService,
    private val openMetadataLookupService: OpenMetadataLookupService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                billingRepository = billingRepository,
                libraryRepository = libraryRepository,
                epubImporter = epubImporter,
                bookEditor = bookEditor,
                bookDeleter = bookDeleter,
                preparedEpubService = preparedEpubService,
                syncService = syncService,
                syncWorkScheduler = syncWorkScheduler,
                aiPreferenceRepository = aiPreferenceRepository,
                aiCoverPromptRepository = aiCoverPromptRepository,
                kindleEmailPreferenceRepository = kindleEmailPreferenceRepository,
                libraryDisplayPreferenceRepository = libraryDisplayPreferenceRepository,
                openAiApiService = openAiApiService,
                openMetadataLookupService = openMetadataLookupService,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
