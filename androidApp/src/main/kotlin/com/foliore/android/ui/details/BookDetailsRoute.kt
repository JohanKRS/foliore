package com.foliore.android.ui.details

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.foliore.android.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.ui.library.noticeMessage
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun BookDetailsRoute(
    viewModel: LibraryViewModel,
    bookId: String,
    onEditMetadata: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val book = state.books.firstOrNull { it.book.id == bookId }
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val shareChooserTitle = stringResource(R.string.export_share_chooser)
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.replaceCover(bookId, uri)
            }
        },
    )
    val message = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        BookDetailsScreen(
            book = book,
            onEditMetadata = onEditMetadata,
            onReplaceCover = { coverPicker.launch("image/*") },
            onPrepareEpub = { viewModel.prepareEpub(bookId) },
            onShareExport = {
                viewModel.sharePreparedEpub(bookId) { intent ->
                    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                }
            },
            onDeleteBook = { showDeleteDialog = true },
            onBack = onBack,
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (showDeleteDialog && book != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.delete_book_dialog_title)) },
            text = { Text(text = stringResource(R.string.delete_book_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteBook(bookId, onDeleted = onBack)
                    },
                ) {
                    Text(text = stringResource(R.string.delete_book_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.delete_book_cancel))
                }
            },
        )
    }
}
