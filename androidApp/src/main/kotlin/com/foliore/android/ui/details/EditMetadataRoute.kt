package com.foliore.android.ui.details

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    onBottomBarActionsChange: (EditBottomBarActions?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val book = state.books.firstOrNull { it.book.id == bookId }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = notice?.type?.let { noticeMessage(it) }
    val shareChooserTitle = stringResource(R.string.export_share_chooser)
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.replaceCover(bookId, uri)
            }
        },
    )

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
            aiProviderMode = state.aiProviderMode,
            aiCoverPromptRecords = state.aiCoverPromptRecords,
            onBack = onBack,
            onSave = { draft ->
                viewModel.saveMetadataAndPrepare(
                    bookId = bookId,
                    draft = draft,
                    onSaved = onBack,
                )
            },
            onShareExport = {
                viewModel.sharePreparedEpub(bookId) { intent ->
                    context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                }
            },
            onAutofillClick = viewModel::requestMetadataAutofill,
            onCoverGenerationUnavailable = viewModel::requestCoverGeneration,
            onGenerateMetadataPreview = { prompt, draft, onPreviewReady ->
                viewModel.generateMetadataPreview(
                    bookId = bookId,
                    prompt = prompt,
                    draft = draft,
                    onPreviewReady = onPreviewReady,
                )
            },
            onGenerateCoverImages = { prompt, draft, reference, styles, onOptionsReady ->
                viewModel.generateCoverImages(
                    bookId = bookId,
                    prompt = prompt,
                    draft = draft,
                    reference = reference,
                    styles = styles,
                    onOptionsReady = onOptionsReady,
                )
            },
            onUseGeneratedCover = { option, record, onUpdated ->
                viewModel.useGeneratedCover(
                    bookId = bookId,
                    option = option,
                    record = record,
                    onUpdated = onUpdated,
                )
            },
            onReplaceCover = { coverPicker.launch("image/*") },
            isLoading = state.hasActiveProgress,
            progress = state.activeProgress,
            onBottomBarActionsChange = onBottomBarActionsChange,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
