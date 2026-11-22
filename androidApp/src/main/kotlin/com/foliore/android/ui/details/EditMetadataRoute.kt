package com.foliore.android.ui.details

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.R
import com.foliore.android.ui.TopChromeContent
import com.foliore.android.ui.library.noticeMessage
import com.foliore.android.ui.library.rememberGmailAuthorizationLauncher
import com.foliore.android.viewmodel.LibraryViewModel
import dev.chrisbanes.haze.HazeState

@Composable
fun EditMetadataRoute(
    viewModel: LibraryViewModel,
    bookId: String,
    onBack: () -> Unit,
    onBottomBarActionsChange: (EditBottomBarActions?) -> Unit,
    chromeHazeState: HazeState? = null,
    topChromeInset: Dp = 0.dp,
    bottomChromeInset: Dp = 0.dp,
    showTopChrome: Boolean = true,
    onTopChromeContentChange: (TopChromeContent?) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gmailAuthorization = rememberGmailAuthorizationLauncher(viewModel)
    val book = state.books.firstOrNull { it.book.id == bookId }
    var showOriginalFiles by rememberSaveable(bookId) { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(bookId) { mutableStateOf(false) }
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomChromeInset),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        EditMetadataScreen(
            book = book,
            readingStarted = state.readingActivity[bookId]?.fraction != null,
            aiCapabilities = state.aiCapabilities,
            aiCoverPromptRecords = state.aiCoverPromptRecords,
            onBack = onBack,
            onSave = { draft ->
                viewModel.saveMetadataAndPrepare(
                    bookId = bookId,
                    draft = draft,
                    onSaved = {},
                )
            },
            onSaveAndNavigate = { draft, destination -> viewModel.saveMetadataAndPrepare(bookId, draft, onSaved = destination) },
            onShareExport = { draft ->
                val export = {
                    viewModel.sharePreparedEpub(bookId) { intent ->
                        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                    }
                }
                if (draft != null) viewModel.saveMetadataAndPrepare(bookId, draft, onSaved = export) else export()
            },
            onSendToKindle = { draft ->
                val send = { viewModel.sendPreparedEpubToKindleEmail(bookId, gmailAuthorization) }
                if (draft != null) viewModel.saveMetadataAndPrepare(bookId, draft, onSaved = send) else send()
            },
            onCoverGenerationUnavailable = viewModel::requestCoverGeneration,
            onLookupMetadataCandidates = { draft, onCandidatesReady ->
                viewModel.lookupMetadataCandidates(
                    bookId = bookId,
                    draft = draft,
                    onCandidatesReady = onCandidatesReady,
                )
            },
            onResolveMetadataCandidate = { candidate, onReady -> viewModel.resolveMetadataCandidate(bookId, candidate, onReady) },
            onUseLookupCover = { path -> viewModel.useMetadataLookupCover(bookId, path) },
            onGenerateMetadataPreview = { prompt, draft, onPreviewReady, onFailure ->
                viewModel.generateMetadataPreview(
                    bookId = bookId,
                    prompt = prompt,
                    draft = draft,
                    onPreviewReady = onPreviewReady,
                    onFailure = onFailure,
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
            onFindCovers = { draft, onOptionsReady ->
                viewModel.findCovers(bookId = bookId, draft = draft, onOptionsReady = onOptionsReady)
            },
            onUseFoundCover = { path, onUpdated -> viewModel.useMetadataLookupCover(bookId, path, onUpdated) },
            onReplaceCover = { coverPicker.launch("image/*") },
            onOriginalFiles = { showOriginalFiles = true },
            onDelete = { showDeleteDialog = true },
            onRead = { draft ->
                val open = { context.startActivity(com.foliore.android.reader.ReaderActivity.intent(context, bookId)) }
                if (draft != null) viewModel.saveMetadataAndPrepare(bookId, draft, onSaved = open) else open()
            },
            isLoading = state.hasActiveProgress,
            progress = state.activeProgress,
            onBottomBarActionsChange = onBottomBarActionsChange,
            chromeHazeState = chromeHazeState,
            topChromeInset = topChromeInset,
            bottomChromeInset = bottomChromeInset,
            showTopChrome = showTopChrome,
            onTopChromeContentChange = onTopChromeContentChange,
            modifier = Modifier.padding(paddingValues),
        )
    }
    if (showOriginalFiles && book != null) {
        OriginalFilesDialog(bookId = bookId, viewModel = viewModel, onDismiss = { showOriginalFiles = false })
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
                        // Unsaved edits belong to the book being removed; leave without the save prompt.
                        viewModel.deleteBook(bookId, onDeleted = onBack)
                    },
                ) {
                    Text(text = stringResource(R.string.delete_book_confirm), color = MaterialTheme.colorScheme.error)
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
