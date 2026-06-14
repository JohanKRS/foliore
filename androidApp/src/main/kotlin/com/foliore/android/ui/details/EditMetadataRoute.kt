package com.foliore.android.ui.details

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.R
import com.foliore.android.ui.library.noticeMessage
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun EditMetadataRoute(
    viewModel: LibraryViewModel,
    bookId: String,
    onBack: () -> Unit,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val book = state.books.firstOrNull { it.book.id == bookId }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = notice?.type?.let { noticeMessage(it) }
    val shareChooserTitle = stringResource(R.string.export_share_chooser)

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        EditMetadataScreen(
            book = book,
            onBack = onBack,
            onSave = { draft ->
                viewModel.saveMetadata(
                    bookId = bookId,
                    draft = draft,
                    onSaved = onBack,
                )
            },
            onPrepareEpub = { viewModel.prepareEpub(bookId) },
            onShareExport = {
                viewModel.sharePreparedEpub(bookId) { intent ->
                    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                }
            },
            onSyncNow = viewModel::syncNow,
            isSyncing = state.isSyncing,
            onLibraryClick = onLibraryClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
