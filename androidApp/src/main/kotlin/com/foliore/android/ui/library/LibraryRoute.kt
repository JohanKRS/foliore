package com.foliore.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onBookClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
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
        onBookClick = onBookClick,
        onBatchModeChange = viewModel::setBatchMode,
        onBookSelectionToggle = viewModel::toggleBookSelection,
        onSelectBooks = viewModel::selectBooks,
        onBatchUpdate = viewModel::applyBatchMetadata,
        onSyncClick = viewModel::syncNow,
        onSettingsClick = onSettingsClick,
    )
}
