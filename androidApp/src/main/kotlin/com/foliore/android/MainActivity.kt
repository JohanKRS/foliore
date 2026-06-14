package com.foliore.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foliore.android.di.AppContainer
import com.foliore.android.navigation.FolioreNavHost
import com.foliore.android.share.IncomingEpubRepository
import com.foliore.android.ui.theme.FolioreTheme
import com.foliore.android.viewmodel.LibraryViewModel
import com.foliore.android.viewmodel.LibraryViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(this) }
    private val incomingEpubRepository by lazy { IncomingEpubRepository(this) }
    private val pendingImportUris = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            enqueueIncomingEpubs(intent)
        }

        setContent {
            val themeMode = appContainer.themePreferenceRepository.themeMode.collectAsStateWithLifecycle()
            val incomingImportUris = pendingImportUris.collectAsStateWithLifecycle()

            FolioreTheme(themeMode = themeMode.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val factory = remember {
                        LibraryViewModelFactory(
                            libraryRepository = appContainer.libraryRepository,
                            epubImporter = appContainer.epubImporter,
                            bookEditor = appContainer.bookEditor,
                            preparedEpubService = appContainer.preparedEpubService,
                            manualSyncService = appContainer.manualSyncService,
                        )
                    }
                    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)

                    LaunchedEffect(incomingImportUris.value) {
                        val uris = incomingImportUris.value
                        if (uris.isNotEmpty()) {
                            libraryViewModel.importUris(uris)
                            pendingImportUris.value = emptyList()
                        }
                    }

                    FolioreNavHost(
                        libraryViewModel = libraryViewModel,
                        themeMode = themeMode.value,
                        onThemeModeChange = appContainer.themePreferenceRepository::setThemeMode,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueIncomingEpubs(intent)
    }

    private fun enqueueIncomingEpubs(intent: Intent?) {
        val incomingUris = incomingEpubRepository.urisFromIntent(intent)
        if (incomingUris.isNotEmpty()) {
            pendingImportUris.value = (pendingImportUris.value + incomingUris).distinct()
        }
    }
}
