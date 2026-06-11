package com.foliore.android.ui.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
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
    val book = state.books.firstOrNull { it.book.id == bookId }
    val snackbarHostState = remember { SnackbarHostState() }
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
            onBack = onBack,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
