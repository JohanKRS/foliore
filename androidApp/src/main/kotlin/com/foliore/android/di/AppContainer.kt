package com.foliore.android.di

import android.content.Context
import com.foliore.android.storage.AiCoverPromptRepository
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.DeviceIdProvider
import com.foliore.android.storage.ManualSyncService
import com.foliore.android.storage.ThemePreferenceRepository
import com.foliore.shared.db.DatabaseDriverFactory
import com.foliore.shared.library.LibraryRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val driver = DatabaseDriverFactory(appContext).createDriver()
    private val deviceIdProvider = DeviceIdProvider(appContext)

    val libraryRepository = LibraryRepository(driver)
    val themePreferenceRepository = ThemePreferenceRepository(appContext)
    val aiPreferenceRepository = AiPreferenceRepository(appContext)
    val aiCoverPromptRepository = AiCoverPromptRepository(appContext)
    val epubImporter = AndroidEpubImporter(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
    )
    val bookEditor = AndroidBookEditor(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
    )
    val bookDeleter = AndroidBookDeleter(
        context = appContext,
        libraryRepository = libraryRepository,
    )
    val preparedEpubService = AndroidPreparedEpubService(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
    )
    val manualSyncService = ManualSyncService(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
        aiCoverPromptRepository = aiCoverPromptRepository,
    )
}
