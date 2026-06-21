package com.foliore.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.viewmodel.LibraryViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onBookClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    chromeHazeState: HazeState? = null,
    bottomChromeInset: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = viewModel::importUris,
    )

    val noticeMessage = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            viewModel.consumeNotice()
        }
    }

    LibraryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onImportClick = {
            picker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
        },
        onSearchClick = { viewModel.setSearchOpen(true) },
        onCloseSearch = { viewModel.setSearchOpen(false) },
        onQueryChange = viewModel::setQuery,
        onFilterChange = viewModel::setFilter,
        onSortOrderChange = viewModel::setSortOrder,
        onSeriesFilterChange = viewModel::setSeriesFilter,
        onGridColumnsChange = viewModel::setGridColumns,
        onShowBookTitleChange = viewModel::setShowBookTitle,
        onShowBookAuthorChange = viewModel::setShowBookAuthor,
        onShowBookSeriesChange = viewModel::setShowBookSeries,
        onBookClick = onBookClick,
        onBatchModeChange = viewModel::setBatchMode,
        onBookSelectionToggle = viewModel::toggleBookSelection,
        onSelectBooks = viewModel::selectBooks,
        onBatchUpdate = viewModel::applyBatchMetadata,
        onBatchDelete = viewModel::deleteSelectedBooks,
        onSettingsClick = onSettingsClick,
        chromeHazeState = chromeHazeState,
        bottomChromeInset = bottomChromeInset,
    )
}
