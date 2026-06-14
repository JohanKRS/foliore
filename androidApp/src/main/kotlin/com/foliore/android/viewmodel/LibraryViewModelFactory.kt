package com.foliore.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.foliore.android.ai.CodexBridgeService
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.storage.AiCoverPromptRepository
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.ManualSyncService
import com.foliore.shared.library.LibraryRepository

class LibraryViewModelFactory(
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                libraryRepository = libraryRepository,
                epubImporter = epubImporter,
                bookEditor = bookEditor,
                bookDeleter = bookDeleter,
                preparedEpubService = preparedEpubService,
                manualSyncService = manualSyncService,
                aiPreferenceRepository = aiPreferenceRepository,
                aiCoverPromptRepository = aiCoverPromptRepository,
                openAiApiService = openAiApiService,
                codexBridgeService = codexBridgeService,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
