package com.foliore.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.shared.library.LibraryRepository

class LibraryViewModelFactory(
    private val libraryRepository: LibraryRepository,
    private val epubImporter: AndroidEpubImporter,
    private val bookEditor: AndroidBookEditor,
    private val preparedEpubService: AndroidPreparedEpubService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            return LibraryViewModel(
                libraryRepository = libraryRepository,
                epubImporter = epubImporter,
                bookEditor = bookEditor,
                preparedEpubService = preparedEpubService,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
