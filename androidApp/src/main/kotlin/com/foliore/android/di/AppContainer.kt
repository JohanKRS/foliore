package com.foliore.android.di

import android.content.Context
import com.foliore.android.storage.AndroidBookEditor
import com.foliore.android.storage.AndroidEpubImporter
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.DeviceIdProvider
import com.foliore.android.storage.ThemePreferenceRepository
import com.foliore.shared.db.DatabaseDriverFactory
import com.foliore.shared.library.LibraryRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val driver = DatabaseDriverFactory(appContext).createDriver()
    private val deviceIdProvider = DeviceIdProvider(appContext)

    val libraryRepository = LibraryRepository(driver)
    val themePreferenceRepository = ThemePreferenceRepository(appContext)
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
    val preparedEpubService = AndroidPreparedEpubService(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
    )
}
