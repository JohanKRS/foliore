package com.foliore.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foliore.android.di.AppContainer
import com.foliore.android.navigation.FolioreNavHost
import com.foliore.android.ui.theme.FolioreTheme
import com.foliore.android.viewmodel.LibraryViewModel
import com.foliore.android.viewmodel.LibraryViewModelFactory

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode = appContainer.themePreferenceRepository.themeMode.collectAsStateWithLifecycle()

            FolioreTheme(themeMode = themeMode.value) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val factory = remember {
                        LibraryViewModelFactory(
                            libraryRepository = appContainer.libraryRepository,
                            epubImporter = appContainer.epubImporter,
                            bookEditor = appContainer.bookEditor,
                            preparedEpubService = appContainer.preparedEpubService,
                        )
                    }
                    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)

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
