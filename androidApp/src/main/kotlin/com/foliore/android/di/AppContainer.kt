package com.foliore.android.di

import android.content.Context
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.metadata.GoogleBooksMetadataService
import com.foliore.android.metadata.OpenMetadataLookupService
import com.foliore.android.metadata.OpenLibraryMetadataService
import com.foliore.android.storage.AiCoverPromptRepository
import com.foliore.android.storage.AiPreferenceRepository
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidBookDeleter
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.DeviceIdProvider
import com.foliore.android.storage.GoogleDriveAuthService
import com.foliore.android.storage.GoogleDriveSyncService
import com.foliore.android.storage.KindleEmailPreferenceRepository
import com.foliore.android.storage.ManualSyncService
import com.foliore.android.storage.MetadataPreferenceRepository
import com.foliore.android.storage.SyncService
import com.foliore.android.storage.SyncSettingsRepository
import com.foliore.android.storage.SyncWorkScheduler
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
    val kindleEmailPreferenceRepository = KindleEmailPreferenceRepository(appContext)
    val metadataPreferenceRepository = MetadataPreferenceRepository(appContext)
    val syncSettingsRepository = SyncSettingsRepository(appContext)
    val googleDriveAuthService = GoogleDriveAuthService(appContext)
    val openAiApiService = OpenAiApiService(
        context = appContext,
        aiPreferenceRepository = aiPreferenceRepository,
    )
    val openLibraryMetadataService = OpenLibraryMetadataService(appContext)
    val googleBooksMetadataService = GoogleBooksMetadataService(
        context = appContext,
        metadataPreferenceRepository = metadataPreferenceRepository,
    )
    val openMetadataLookupService = OpenMetadataLookupService(
        openLibraryMetadataService = openLibraryMetadataService,
        googleBooksMetadataService = googleBooksMetadataService,
    )
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
    val googleDriveSyncService = GoogleDriveSyncService(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
        aiCoverPromptRepository = aiCoverPromptRepository,
        googleDriveAuthService = googleDriveAuthService,
        syncSettingsRepository = syncSettingsRepository,
    )
    val syncService = SyncService(
        manualSyncService = manualSyncService,
        googleDriveSyncService = googleDriveSyncService,
        googleDriveAuthService = googleDriveAuthService,
        syncSettingsRepository = syncSettingsRepository,
    )
    val syncWorkScheduler = SyncWorkScheduler(
        context = appContext,
        syncSettingsRepository = syncSettingsRepository,
    )
}
