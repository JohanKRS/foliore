package com.foliore.android

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.foliore.android.ui.tuning.VisualTuningHost
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
        allowDisplayCutout()
        if (savedInstanceState == null) {
            enqueueIncomingEpubs(intent)
        }

        setContent {
            val themeMode = appContainer.themePreferenceRepository.themeMode.collectAsStateWithLifecycle()
            val incomingImportUris = pendingImportUris.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode.value) {
                com.foliore.android.ui.theme.ThemeMode.SYSTEM -> systemDarkTheme
                com.foliore.android.ui.theme.ThemeMode.LIGHT -> false
                com.foliore.android.ui.theme.ThemeMode.DARK -> true
            }

            LaunchedEffect(darkTheme) {
                val lightSystemBar = Color.rgb(248, 243, 234)
                val darkSystemBar = Color.rgb(17, 20, 17)
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(darkSystemBar)
                } else {
                    SystemBarStyle.light(lightSystemBar, darkSystemBar)
                }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
                allowDisplayCutout()
            }

            FolioreTheme(themeMode = themeMode.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    VisualTuningHost {
                        val factory = remember {
                            LibraryViewModelFactory(
                                libraryRepository = appContainer.libraryRepository,
                                epubImporter = appContainer.epubImporter,
                                bookEditor = appContainer.bookEditor,
                                bookDeleter = appContainer.bookDeleter,
                                preparedEpubService = appContainer.preparedEpubService,
                                syncService = appContainer.syncService,
                                syncWorkScheduler = appContainer.syncWorkScheduler,
                                aiPreferenceRepository = appContainer.aiPreferenceRepository,
                                aiCoverPromptRepository = appContainer.aiCoverPromptRepository,
                                kindleEmailPreferenceRepository = appContainer.kindleEmailPreferenceRepository,
                                libraryDisplayPreferenceRepository = appContainer.libraryDisplayPreferenceRepository,
                                metadataPreferenceRepository = appContainer.metadataPreferenceRepository,
                                openAiApiService = appContainer.openAiApiService,
                                openMetadataLookupService = appContainer.openMetadataLookupService,
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
