package com.foliore.android.di

import android.content.Context
import com.foliore.android.ai.OpenAiApiService
import com.foliore.android.metadata.OpenMetadataLookupService
import com.foliore.android.metadata.OpenLibraryMetadataService
import com.foliore.android.reader.ReadingStateRepository
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
import com.foliore.android.storage.LibraryDisplayPreferenceRepository
import com.foliore.android.storage.ManualSyncService
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

    val billingRepository = com.foliore.android.billing.PlayBillingRepository.get(appContext)
    val libraryRepository = LibraryRepository(driver)
    val launcherIconRepository by lazy {
        com.foliore.android.storage.LauncherIconRepository(appContext)
    }
    val themePreferenceRepository = ThemePreferenceRepository(appContext)
    val aiPreferenceRepository = AiPreferenceRepository(appContext)
    val aiCoverPromptRepository = AiCoverPromptRepository(appContext)
    val kindleEmailPreferenceRepository = KindleEmailPreferenceRepository(appContext)
    val gmailAuthService = com.foliore.android.storage.GmailAuthService(appContext)
    val libraryDisplayPreferenceRepository = LibraryDisplayPreferenceRepository(appContext)
    val readingStateRepository = ReadingStateRepository(appContext)
    val syncSettingsRepository = SyncSettingsRepository(appContext)
    val googleDriveAuthService = GoogleDriveAuthService(
        context = appContext,
        syncSettingsRepository = syncSettingsRepository,
    )
    val openAiApiService = OpenAiApiService(
        context = appContext,
        aiPreferenceRepository = aiPreferenceRepository,
    )
    val openLibraryMetadataService = OpenLibraryMetadataService(appContext)
    val openMetadataLookupService = OpenMetadataLookupService(
        openLibraryMetadataService = openLibraryMetadataService,
        cacheDir = appContext.cacheDir,
    )
    val epubImporter = AndroidEpubImporter(
        isUnlimited = { billingRepository.state.value.unlimited },
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
    val originalFilesService = com.foliore.android.storage.AndroidOriginalFilesService(appContext, libraryRepository)
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
