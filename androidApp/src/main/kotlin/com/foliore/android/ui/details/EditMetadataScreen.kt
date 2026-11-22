package com.foliore.android.ui.details

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.ai.AiCapabilities
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.android.ai.AiProvider
import com.foliore.android.ai.AiCoverStyleRequest
import com.foliore.android.ai.AiGeneratedCoverOption
import com.foliore.android.metadata.MetadataLookupCandidate
import com.foliore.android.metadata.MetadataLanguages
import com.foliore.android.metadata.MetadataField
import com.foliore.android.metadata.applySelectedMetadata
import com.foliore.android.ui.TopChromeContent
import com.foliore.android.ui.TopChromeDividerHalfHeight
import com.foliore.android.ui.TopChromeHeaderHeight
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.noticeMessage
import com.foliore.android.ui.library.FolioreOptionsDialog
import com.foliore.android.ui.library.StatusPill
import com.foliore.android.ui.library.SyncProgressStatusLine
import com.foliore.android.ui.theme.CormorantGaramondFamily
import com.foliore.android.ui.tuning.LocalVisualTuning
import com.foliore.android.viewmodel.LibraryNoticeType
import com.foliore.android.viewmodel.SyncProgress
import com.foliore.android.viewmodel.SyncProgressPhase
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File

@Composable
fun EditMetadataScreen(
    book: LibraryBook?,
    readingStarted: Boolean,
    aiCapabilities: AiCapabilities,
    aiCoverPromptRecords: List<AiCoverPromptRecord>,
    onBack: () -> Unit,
    onSave: (BookMetadataDraft) -> Unit,
    onShareExport: (BookMetadataDraft?) -> Unit,
    onSendToKindle: (BookMetadataDraft?) -> Unit,
    onSaveAndNavigate: (BookMetadataDraft, () -> Unit) -> Unit,
    onCoverGenerationUnavailable: () -> Unit,
    onLookupMetadataCandidates: (BookMetadataDraft, (List<MetadataLookupCandidate>) -> Unit) -> Unit,
    onResolveMetadataCandidate: (MetadataLookupCandidate, (MetadataLookupCandidate) -> Unit) -> Unit,
    onUseLookupCover: (String) -> Unit,
    onGenerateMetadataPreview: (
        String,
        BookMetadataDraft,
        (BookMetadataDraft) -> Unit,
        (LibraryNoticeType, String?) -> Unit,
    ) -> Unit,
    onGenerateCoverImages: (
        String,
        BookMetadataDraft,
        AiCoverPromptRecord?,
        List<AiCoverStyleRequest>,
        (List<AiGeneratedCoverOption>) -> Unit,
    ) -> Unit,
    onUseGeneratedCover: (AiGeneratedCoverOption, AiCoverPromptRecord, () -> Unit) -> Unit,
    onFindCovers: (BookMetadataDraft, (List<AiGeneratedCoverOption>) -> Unit) -> Unit,
    onUseFoundCover: (String, () -> Unit) -> Unit,
    onReplaceCover: () -> Unit,
    onOriginalFiles: () -> Unit,
    onDelete: () -> Unit,
    onRead: (BookMetadataDraft?) -> Unit,
    isLoading: Boolean,
    progress: SyncProgress?,
    onBottomBarActionsChange: (EditBottomBarActions?) -> Unit,
    chromeHazeState: HazeState? = null,
    topChromeInset: Dp = 0.dp,
    bottomChromeInset: Dp = 0.dp,
    showTopChrome: Boolean = true,
    onTopChromeContentChange: (TopChromeContent?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (book == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            EditTopBar(onBack = onBack)
            EditorialDivider(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.details_book_not_found),
                modifier = Modifier.padding(28.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val aiTextAvailable = aiCapabilities.canFillText
    val displayLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var languageAnchor by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.language) }
    var identifiers by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.identifiers) }
    var title by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.title) }
    var subtitle by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.subtitle.orEmpty()) }
    var authorsAnchor by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.authors) }
    var tagsAnchor by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.tags) }
    var authors by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.authors.joinToString(", ")) }
    var sortAuthor by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.sortAuthor.orEmpty()) }
    var seriesName by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.seriesName.orEmpty()) }
    var seriesIndex by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.seriesIndex.formatSeriesIndex()) }
    var language by rememberSaveable(book.book.id) { mutableStateOf(MetadataLanguages.display(book.metadata.language, displayLocale)) }
    var publisher by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publisher.orEmpty()) }
    var publishedDate by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publishedDate.orEmpty()) }
    var description by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.description.orEmpty()) }
    var tags by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.tags.joinToString(", ")) }
    var isbn by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.isbn.orEmpty()) }
    var titleTouched by remember { mutableStateOf(false) }
    var showMetadataAutofillDialog by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var showCoverGenerationDialog by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var showDescriptionEditor by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var metadataPreview by remember(book.book.id) { mutableStateOf<MetadataAutofillPreview?>(null) }
    // The AI preview runs outside the shared progress slot, so the dialog tracks it here.
    var metadataGenerationStartedAt by remember(book.book.id) { mutableStateOf<Long?>(null) }
    var metadataGenerationError by remember(book.book.id) { mutableStateOf<AiPreviewFailure?>(null) }
    var lookupCandidates by remember(book.book.id) { mutableStateOf<List<MetadataLookupCandidate>>(emptyList()) }
    var lookupSession by remember(book.book.id) { mutableStateOf(0) }
    var resolvingCandidateId by remember(book.book.id) { mutableStateOf<String?>(null) }
    var selectedLookupCandidateId by rememberSaveable(book.book.id) { mutableStateOf<String?>(null) }
    var coverOptions by remember(book.book.id) { mutableStateOf<List<AiGeneratedCoverOption>>(emptyList()) }
    var selectedCoverOptionId by rememberSaveable(book.book.id) { mutableStateOf<String?>(null) }
    var coverGenerationRound by rememberSaveable(book.book.id) { mutableStateOf(0) }
    var foundCoverOptions by remember(book.book.id) { mutableStateOf<List<AiGeneratedCoverOption>>(emptyList()) }
    var selectedFoundCoverId by rememberSaveable(book.book.id) { mutableStateOf<String?>(null) }
    val defaultMetadataPrompt = stringResource(R.string.ai_metadata_default_prompt)
    val defaultCoverPrompt = stringResource(R.string.ai_cover_default_prompt)
    var metadataPrompt by rememberSaveable(book.book.id) { mutableStateOf(defaultMetadataPrompt) }
    var coverPrompt by rememberSaveable(book.book.id) { mutableStateOf(defaultCoverPrompt) }
    val seriesPromptReference = remember(book.book.id, book.metadata.seriesName, aiCoverPromptRecords) {
        aiCoverPromptRecords.coverPromptReferenceFor(book)
    }
    val titleError = titleTouched && title.isBlank()
    val isLookingUpMetadata = isLoading && progress?.phase == SyncProgressPhase.LOOKING_UP_METADATA
    val isGeneratingMetadata = metadataGenerationStartedAt != null
    val isGeneratingCovers = isLoading && progress?.phase == SyncProgressPhase.GENERATING_COVERS
    val isFindingCovers = isLoading && progress?.phase == SyncProgressPhase.FINDING_COVERS
    val currentDraft = {
        BookMetadataDraft(
            title = title,
            subtitle = subtitle.cleanOptional(),
            authors = if (authors == authorsAnchor.joinToString(", ")) authorsAnchor else authors.splitValues(),
            sortAuthor = sortAuthor.cleanOptional(),
            language = MetadataLanguages.fromDisplay(language, languageAnchor, displayLocale),
            publisher = publisher.cleanOptional(),
            publishedDate = publishedDate.cleanOptional(),
            description = description.cleanOptional(),
            tags = if (tags == tagsAnchor.joinToString(", ")) tagsAnchor else tags.splitValues(),
            isbn = isbn.cleanOptional(),
            identifiers = identifiers,
            seriesName = seriesName.cleanOptional(),
            seriesIndex = seriesIndex.cleanOptional()?.toDoubleOrNull(),
        )
    }
    val originalDraft = remember(book.book.id, book.metadata) {
        book.toMetadataDraft().let { it.copy(language = MetadataLanguages.code(it.language) ?: it.language) }
    }
    val hasPendingSave = title.isNotBlank() && currentDraft() != originalDraft
    val saveDraft = {
        titleTouched = true
        if (hasPendingSave) {
            onSave(currentDraft())
        }
    }
    val latestSaveDraft by rememberUpdatedState(saveDraft)
    val latestShareExport by rememberUpdatedState({ onShareExport(currentDraft().takeIf { it != originalDraft }) })
    val latestSendToKindle by rememberUpdatedState({ onSendToKindle(currentDraft().takeIf { it != originalDraft }) })
    val latestRead by rememberUpdatedState({ onRead(currentDraft().takeIf { it != originalDraft }) })
    val hasUnsavedChanges = currentDraft() != originalDraft
    var showUnsavedChanges by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var pendingExit by remember(book.book.id) { mutableStateOf<(() -> Unit)?>(null) }
    val requestExit: (() -> Unit) -> Unit = { destination ->
        if (currentDraft() != originalDraft) { pendingExit = destination; showUnsavedChanges = true }
        else destination()
    }
    val latestRequestExit by rememberUpdatedState(requestExit)
    val guardedBack = { requestExit(onBack) }
    BackHandler(enabled = currentDraft() != originalDraft) { guardedBack() }
    if (showUnsavedChanges) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnsavedChanges = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onSaveAndNavigate(currentDraft(), pendingExit ?: onBack) }, enabled = title.isNotBlank()) {
                    Text(stringResource(R.string.edit_metadata_save))
                }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = { showUnsavedChanges = false }) { Text(stringResource(R.string.action_cancel)) }
                    androidx.compose.material3.TextButton(onClick = { (pendingExit ?: onBack)() }) { Text(stringResource(R.string.discard_changes)) }
                }
            },
        )
    }
    val applyMetadataDraft: (BookMetadataDraft) -> Unit = { draft ->
        title = draft.title
        subtitle = draft.subtitle.orEmpty()
        authorsAnchor = draft.authors
        tagsAnchor = draft.tags
        authors = draft.authors.joinToString(", ")
        sortAuthor = draft.sortAuthor.orEmpty()
        seriesName = draft.seriesName.orEmpty()
        seriesIndex = draft.seriesIndex.formatSeriesIndex()
        languageAnchor = draft.language
        language = MetadataLanguages.display(draft.language, displayLocale)
        identifiers = draft.identifiers
        publisher = draft.publisher.orEmpty()
        publishedDate = draft.publishedDate.orEmpty()
        description = draft.description.orEmpty()
        tags = draft.tags.joinToString(", ")
        isbn = draft.isbn.orEmpty()
        titleTouched = true
        showMetadataAutofillDialog = false
    }
    val applyMetadataPreview: (MetadataAutofillPreview) -> Unit = { preview ->
        title = preview.title
        subtitle = preview.subtitle.orEmpty()
        authorsAnchor = preview.authors
        tagsAnchor = preview.tags
        authors = preview.authors.joinToString(", ")
        sortAuthor = preview.sortAuthor.orEmpty()
        seriesName = preview.seriesName.orEmpty()
        seriesIndex = preview.seriesIndex.formatSeriesIndex()
        languageAnchor = preview.language
        language = MetadataLanguages.display(preview.language, displayLocale)
        publisher = preview.publisher.orEmpty()
        publishedDate = preview.publishedDate.orEmpty()
        description = preview.description.orEmpty()
        tags = preview.tags.joinToString(", ")
        isbn = preview.isbn.orEmpty()
        titleTouched = true
        showMetadataAutofillDialog = false
    }
    val optionAStyle = stringResource(
        if (coverGenerationRound % 2 == 0) {
            R.string.ai_cover_option_a_style
        } else {
            R.string.ai_cover_option_a_alt_style
        },
    )
    val optionBStyle = stringResource(
        if (coverGenerationRound % 2 == 0) {
            R.string.ai_cover_option_b_style
        } else {
            R.string.ai_cover_option_b_alt_style
        },
    )
    val optionALabel = stringResource(R.string.ai_cover_option_a)
    val optionBLabel = stringResource(R.string.ai_cover_option_b)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val tabletWorkspace = maxWidth >= 840.dp
        val compactActions = maxWidth < maxHeight
        val saveEnabled = hasPendingSave
        val localHazeState = rememberHazeState()
        val hazeState = chromeHazeState ?: localHazeState
        val currentOnBack by rememberUpdatedState(guardedBack)
        val currentOnAutofillClick by rememberUpdatedState {
            lookupSession++
            lookupCandidates = emptyList()
            selectedLookupCandidateId = null
            showMetadataAutofillDialog = true
        }
        val externalTopChromeContent: TopChromeContent = remember(
            hazeState,
            tabletWorkspace,
            aiTextAvailable,
        ) {
            { chromeModifier ->
                EditMetadataTopChromeOverlay(
                    hazeState = hazeState,
                    tabletWorkspace = tabletWorkspace,
                    aiTextAvailable = aiTextAvailable,
                    onBack = { currentOnBack() },
                    onAutofillClick = { currentOnAutofillClick() },
                    modifier = chromeModifier,
                    drawBackground = false,
                )
            }
        }

        DisposableEffect(tabletWorkspace, compactActions, saveEnabled, hasUnsavedChanges, readingStarted) {
            onBottomBarActionsChange(
                EditBottomBarActions(
                    onSave = { latestSaveDraft() },
                    onRead = { latestRead() },
                    onRequestNavigate = { destination -> latestRequestExit(destination) },
                    onShareExport = { latestShareExport() },
                    onSendToKindle = { latestSendToKindle() },
                    saveEnabled = saveEnabled,
                    hasPendingSave = hasPendingSave,
                    hasUnsavedChanges = hasUnsavedChanges,
                    readingStarted = readingStarted,
                    compact = !tabletWorkspace || compactActions,
                ),
            )

            onDispose {
                onBottomBarActionsChange(null)
            }
        }

        DisposableEffect(showTopChrome, externalTopChromeContent) {
            if (!showTopChrome) {
                onTopChromeContentChange(externalTopChromeContent)
            }
            onDispose {
                if (!showTopChrome) {
                    onTopChromeContentChange(null)
                }
            }
        }

        SideEffect {
            if (!showTopChrome) {
                onTopChromeContentChange(externalTopChromeContent)
            }
        }

        if (tabletWorkspace) {
            TabletEditBookScreen(
                book = book,
                hazeState = hazeState,
                topChromeInset = topChromeInset,
                bottomChromeInset = bottomChromeInset,
                showTopChrome = showTopChrome,
                title = title,
                onTitleChange = {
                    title = it
                    titleTouched = true
                },
                titleError = titleError,
                subtitle = subtitle,
                onSubtitleChange = { subtitle = it },
                authors = authors,
                onAuthorsChange = { authors = it },
                seriesName = seriesName,
                onSeriesNameChange = { seriesName = it },
                seriesIndex = seriesIndex,
                onSeriesIndexChange = { seriesIndex = it },
                language = language,
                onLanguageChange = { language = it },
                publisher = publisher,
                onPublisherChange = { publisher = it },
                publishedDate = publishedDate,
                onPublishedDateChange = { publishedDate = it },
                tags = tags,
                onTagsChange = { tags = it },
                isbn = isbn,
                onIsbnChange = { isbn = it },
                description = description,
                onEditDescription = { showDescriptionEditor = true },
                onRead = { onRead(currentDraft().takeIf { it != originalDraft }) },
                onOriginalFiles = onOriginalFiles,
                onDelete = onDelete,
                originalFilesEnabled = !isLoading,
                onBack = guardedBack,
                onReplaceCover = onReplaceCover,
                onGenerateCoverClick = { showCoverGenerationDialog = true },
                aiTextAvailable = aiTextAvailable,
                aiCapabilities = aiCapabilities,
                onAutofillClick = { lookupSession++; lookupCandidates = emptyList(); selectedLookupCandidateId = null; showMetadataAutofillDialog = true },
            )
        } else {
            StackedEditBookScreen(
                book = book,
                hazeState = hazeState,
                topChromeInset = topChromeInset,
                bottomChromeInset = bottomChromeInset,
                showTopChrome = showTopChrome,
                aiTextAvailable = aiTextAvailable,
                aiCapabilities = aiCapabilities,
                title = title,
                onTitleChange = {
                    title = it
                    titleTouched = true
                },
                titleError = titleError,
                subtitle = subtitle,
                onSubtitleChange = { subtitle = it },
                authors = authors,
                onAuthorsChange = { authors = it },
                seriesName = seriesName,
                onSeriesNameChange = { seriesName = it },
                seriesIndex = seriesIndex,
                onSeriesIndexChange = { seriesIndex = it },
                language = language,
                onLanguageChange = { language = it },
                publisher = publisher,
                onPublisherChange = { publisher = it },
                publishedDate = publishedDate,
                onPublishedDateChange = { publishedDate = it },
                description = description,
                onEditDescription = { showDescriptionEditor = true },
                onRead = { onRead(currentDraft().takeIf { it != originalDraft }) },
                onOriginalFiles = onOriginalFiles,
                onDelete = onDelete,
                originalFilesEnabled = !isLoading,
                tags = tags,
                onTagsChange = { tags = it },
                isbn = isbn,
                onIsbnChange = { isbn = it },
                onAutofillClick = { lookupSession++; lookupCandidates = emptyList(); selectedLookupCandidateId = null; showMetadataAutofillDialog = true },
                onGenerateCoverClick = { showCoverGenerationDialog = true },
                onReplaceCover = onReplaceCover,
                onBack = guardedBack,
            )
        }
    }

    if (showMetadataAutofillDialog) {
        val session = lookupSession
        LaunchedEffect(selectedLookupCandidateId, session) {
            val candidate = lookupCandidates.firstOrNull { it.id == selectedLookupCandidateId }
            resolvingCandidateId = candidate?.id?.takeUnless { candidate.detailsResolved }
            if (candidate != null && !candidate.detailsResolved) {
                onResolveMetadataCandidate(candidate) { resolved ->
                    if (showMetadataAutofillDialog && lookupSession == session) {
                        lookupCandidates = lookupCandidates.map { if (it.id == resolved.id) resolved else it }
                        if (resolvingCandidateId == resolved.id) resolvingCandidateId = null
                    }
                }
            }
        }
        MetadataAutofillDialog(
            prompt = metadataPrompt,
            onPromptChange = { metadataPrompt = it },
            preview = metadataPreview,
            aiTextAvailable = aiTextAvailable,
            usesChatGptPlan = aiCapabilities.textProvider == AiProvider.CHATGPT,
            lookupCandidates = lookupCandidates,
            selectedLookupCandidateId = selectedLookupCandidateId,
            isLookingUpMetadata = isLookingUpMetadata,
            isResolvingCandidate = resolvingCandidateId == selectedLookupCandidateId && resolvingCandidateId != null,
            isGenerating = isGeneratingMetadata,
            generationStartedAt = metadataGenerationStartedAt,
            generationError = metadataGenerationError,
            progress = progress,
            onLookupMetadata = {
                if (!isLookingUpMetadata) {
                    onLookupMetadataCandidates(currentDraft()) { candidates ->
                        if (showMetadataAutofillDialog && lookupSession == session) {
                            lookupCandidates = candidates
                            selectedLookupCandidateId = candidates.firstOrNull()?.id
                        }
                    }
                }
            },
            onSelectLookupCandidate = { selectedLookupCandidateId = it },
            onApplyLookupCandidate = { candidate, fields ->
                applyMetadataDraft(currentDraft().applySelectedMetadata(candidate.metadata, fields))
                if (MetadataField.COVER in fields) candidate.coverImagePath?.let(onUseLookupCover)
            },
            onGeneratePreview = {
                if (!isGeneratingMetadata && aiTextAvailable) {
                    metadataGenerationError = null
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    metadataGenerationStartedAt = startedAt
                    onGenerateMetadataPreview(
                        metadataPrompt,
                        currentDraft(),
                        { previewDraft ->
                            if (metadataGenerationStartedAt == startedAt) {
                                metadataPreview = previewDraft.toAutofillPreview()
                                metadataGenerationStartedAt = null
                            }
                        },
                        { failure, detail ->
                            if (metadataGenerationStartedAt == startedAt) {
                                metadataGenerationError = AiPreviewFailure(failure, detail)
                                metadataGenerationStartedAt = null
                            }
                        },
                    )
                }
            },
            onApplyPreview = applyMetadataPreview,
            onDismiss = { showMetadataAutofillDialog = false; lookupSession++; resolvingCandidateId = null },
        )
    }

    if (showDescriptionEditor) {
        DescriptionEditorDialog(
            description = description,
            onApply = { edited ->
                description = edited
                showDescriptionEditor = false
            },
            onDismiss = { showDescriptionEditor = false },
        )
    }

    if (showCoverGenerationDialog) {
        CoverGenerationDialog(
            canGenerate = aiCapabilities.canGenerateCovers,
            canFind = aiCapabilities.canFindCovers,
            usesChatGptPlan = aiCapabilities.textProvider == AiProvider.CHATGPT,
            foundOptions = foundCoverOptions,
            selectedFoundOptionId = selectedFoundCoverId,
            isFinding = isFindingCovers,
            onFind = {
                if (!isFindingCovers) {
                    onFindCovers(currentDraft()) { found ->
                        foundCoverOptions = found
                        selectedFoundCoverId = found.firstOrNull()?.id
                    }
                }
            },
            onSelectFoundOption = { selectedFoundCoverId = it },
            onUseFound = {
                foundCoverOptions.firstOrNull { it.id == selectedFoundCoverId }?.imagePath?.let { path ->
                    onUseFoundCover(path) { showCoverGenerationDialog = false }
                }
            },
            prompt = coverPrompt,
            onPromptChange = { coverPrompt = it },
            seriesPromptReference = seriesPromptReference,
            options = coverOptions,
            selectedOptionId = selectedCoverOptionId,
            isGenerating = isGeneratingCovers,
            progress = progress,
            onGenerate = {
                if (!isGeneratingCovers) {
                    if (!aiCapabilities.canGenerateCovers) {
                        onCoverGenerationUnavailable()
                    } else {
                        val styles = listOf(
                            AiCoverStyleRequest(
                                id = "a-$coverGenerationRound",
                                label = optionALabel,
                                style = optionAStyle,
                            ),
                            AiCoverStyleRequest(
                                id = "b-$coverGenerationRound",
                                label = optionBLabel,
                                style = optionBStyle,
                            ),
                        )
                        onGenerateCoverImages(
                            coverPrompt,
                            currentDraft(),
                            seriesPromptReference,
                            styles,
                        ) { generatedOptions ->
                            coverOptions = generatedOptions
                            selectedCoverOptionId = null
                            coverGenerationRound += 1
                        }
                    }
                }
            },
            onSelectOption = { selectedCoverOptionId = it },
            onUseSelected = {
                val selectedOption = coverOptions.firstOrNull { it.id == selectedCoverOptionId }
                if (selectedOption != null) {
                    onUseGeneratedCover(
                        selectedOption,
                        AiCoverPromptRecord(
                            id = "${book.book.id}-${System.currentTimeMillis()}",
                            bookId = book.book.id,
                            title = book.metadata.title,
                            seriesName = book.metadata.seriesName,
                            seriesIndex = book.metadata.seriesIndex,
                            coverImagePath = selectedOption.imagePath
                                ?: book.book.coverImagePath
                                ?: book.book.coverThumbPath,
                            prompt = selectedOption.prompt,
                            style = selectedOption.style,
                            selectedAtEpochMillis = System.currentTimeMillis(),
                            referenceBookId = seriesPromptReference?.bookId,
                            referencePrompt = seriesPromptReference?.prompt,
                            referenceCoverImagePath = seriesPromptReference?.coverImagePath,
                        ),
                    ) {
                        showCoverGenerationDialog = false
                    }
                } else {
                    onCoverGenerationUnavailable()
                }
            },
            onDismiss = { showCoverGenerationDialog = false },
        )
    }
}

@Composable
private fun TabletEditBookScreen(
    book: LibraryBook,
    hazeState: HazeState,
    topChromeInset: Dp,
    bottomChromeInset: Dp,
    showTopChrome: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: Boolean,
    subtitle: String,
    onSubtitleChange: (String) -> Unit,
    authors: String,
    onAuthorsChange: (String) -> Unit,
    seriesName: String,
    onSeriesNameChange: (String) -> Unit,
    seriesIndex: String,
    onSeriesIndexChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    publisher: String,
    onPublisherChange: (String) -> Unit,
    publishedDate: String,
    onPublishedDateChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    isbn: String,
    onIsbnChange: (String) -> Unit,
    description: String,
    onEditDescription: () -> Unit,
    onOriginalFiles: () -> Unit,
    onDelete: () -> Unit,
    originalFilesEnabled: Boolean,
    onRead: () -> Unit,
    onBack: () -> Unit,
    onReplaceCover: () -> Unit,
    onGenerateCoverClick: () -> Unit,
    aiTextAvailable: Boolean,
    aiCapabilities: AiCapabilities,
    onAutofillClick: () -> Unit,
) {
    val tuning = LocalVisualTuning.current

    Row(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState),
    ) {
        Column(
            modifier = Modifier
                .weight(TabletEditLeftPaneWeight)
                .fillMaxHeight(),
        ) {
            if (showTopChrome) {
                TabletLeftTopBar(onBack = onBack)
                EditorialDivider(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(modifier = Modifier.height(topChromeInset))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = tuning.detailsTabletLeftHorizontalPadding.dp,
                        vertical = tuning.detailsTabletCoverVerticalPadding.dp,
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = tuning.detailsTabletCoverMaxWidth.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    EditBookCover(
                        book = book,
                        onClick = onRead,
                        enabled = originalFilesEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverButtonTopSpacing.dp))
                    CoverActions(
                        aiCapabilities = aiCapabilities,
                        onReplaceCover = onReplaceCover,
                        onGenerateCoverClick = onGenerateCoverClick,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverTitleTopSpacing.dp))
                    Text(
                        text = title.ifBlank { book.metadata.title },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramondFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverAuthorSpacing.dp))
                    Text(
                        text = authors.splitValues().firstOrNull()
                            ?: stringResource(R.string.book_unknown_author),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverStatusSpacing.dp))
                    StatusPill(
                        status = book.book.status,
                        syncStatus = book.book.syncStatus,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (showTopChrome) Modifier else Modifier.padding(top = topChromeInset))
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        )

        Column(
            modifier = Modifier
                .weight(TabletEditRightPaneWeight)
                .fillMaxHeight()
                .padding(horizontal = tuning.detailsPaneHorizontalPadding.dp),
        ) {
            if (showTopChrome) {
                TabletRightHeader(
                    aiTextAvailable = aiTextAvailable,
                    onAutofillClick = onAutofillClick,
                )
                EditorialDivider(modifier = Modifier.fillMaxWidth(), horizontalPadding = 0.dp)
            } else {
                Spacer(modifier = Modifier.height(topChromeInset))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = tuning.detailsPaneVerticalPadding.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TabletMetadataField(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        label = stringResource(R.string.edit_metadata_field_title),
                    ) {
                        MetadataTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            label = stringResource(R.string.edit_metadata_field_title),
                            isError = titleError,
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.AutoMirrored.Outlined.ShortText,
                        label = stringResource(R.string.edit_metadata_field_subtitle),
                    ) {
                        MetadataTextField(
                            value = subtitle,
                            onValueChange = onSubtitleChange,
                            label = stringResource(R.string.edit_metadata_field_subtitle),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.PersonOutline,
                        label = stringResource(R.string.edit_metadata_field_authors),
                    ) {
                        MetadataTextField(
                            value = authors,
                            onValueChange = onAuthorsChange,
                            label = stringResource(R.string.edit_metadata_field_authors),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.BookmarkBorder,
                        label = stringResource(R.string.edit_metadata_field_series),
                    ) {
                        MetadataTextField(
                            value = seriesName,
                            onValueChange = onSeriesNameChange,
                            label = stringResource(R.string.edit_metadata_field_series),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Numbers,
                        label = stringResource(R.string.edit_metadata_field_series_index),
                    ) {
                        MetadataTextField(
                            value = seriesIndex,
                            onValueChange = onSeriesIndexChange,
                            label = stringResource(R.string.edit_metadata_field_series_index),
                            showLabel = false,
                            singleLine = true,
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Language,
                        label = stringResource(R.string.edit_metadata_field_language),
                    ) {
                        MetadataTextField(
                            value = language,
                            onValueChange = onLanguageChange,
                            label = stringResource(R.string.edit_metadata_field_language),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Business,
                        label = stringResource(R.string.edit_metadata_field_publisher),
                    ) {
                        MetadataTextField(
                            value = publisher,
                            onValueChange = onPublisherChange,
                            label = stringResource(R.string.edit_metadata_field_publisher),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.details_published_year),
                    ) {
                        MetadataTextField(
                            value = publishedDate,
                            onValueChange = onPublishedDateChange,
                            label = stringResource(R.string.edit_metadata_field_published_date),
                            showLabel = false,
                            singleLine = true,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.LocalOffer,
                        label = stringResource(R.string.edit_metadata_field_tags),
                    ) {
                        MetadataTextField(
                            value = tags,
                            onValueChange = onTagsChange,
                            label = stringResource(R.string.edit_metadata_field_tags),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.edit_metadata_field_isbn),
                    ) {
                        MetadataTextField(
                            value = isbn,
                            onValueChange = onIsbnChange,
                            label = stringResource(R.string.edit_metadata_field_isbn),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                }

                TabletSyncSection(book = book)
                TabletAboutSection(description = description, onEdit = onEditDescription)
                BookFooterActions(
                    onOriginalFiles = onOriginalFiles,
                    onDelete = onDelete,
                    enabled = originalFilesEnabled,
                )
                Spacer(modifier = Modifier.height(bottomChromeInset))
            }
        }
    }
}

@Composable
private fun StackedEditBookScreen(
    book: LibraryBook,
    hazeState: HazeState,
    topChromeInset: Dp,
    bottomChromeInset: Dp,
    showTopChrome: Boolean,
    aiTextAvailable: Boolean,
    aiCapabilities: AiCapabilities,
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: Boolean,
    subtitle: String,
    onSubtitleChange: (String) -> Unit,
    authors: String,
    onAuthorsChange: (String) -> Unit,
    seriesName: String,
    onSeriesNameChange: (String) -> Unit,
    seriesIndex: String,
    onSeriesIndexChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    publisher: String,
    onPublisherChange: (String) -> Unit,
    publishedDate: String,
    onPublishedDateChange: (String) -> Unit,
    description: String,
    onEditDescription: () -> Unit,
    onOriginalFiles: () -> Unit,
    onDelete: () -> Unit,
    originalFilesEnabled: Boolean,
    onRead: () -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    isbn: String,
    onIsbnChange: (String) -> Unit,
    onAutofillClick: () -> Unit,
    onGenerateCoverClick: () -> Unit,
    onReplaceCover: () -> Unit,
    onBack: () -> Unit,
) {
    val density = LocalDensity.current
    var topChromeHeightPx by remember { mutableStateOf(0) }
    val resolvedTopChromeInset = if (showTopChrome) {
        with(density) { topChromeHeightPx.toDp() }
    } else {
        topChromeInset
    }
    val tuning = LocalVisualTuning.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = resolvedTopChromeInset + tuning.detailsMobileTopExtra.dp,
                    bottom = bottomChromeInset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = tuning.detailsMobileHorizontalPadding.dp)
                    .widthIn(max = tuning.detailsTabletCoverMaxWidth.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = tuning.detailsTabletCoverMaxWidth.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    EditBookCover(
                        book = book,
                        onClick = onRead,
                        enabled = originalFilesEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverButtonTopSpacing.dp))
                    CoverActions(
                        aiCapabilities = aiCapabilities,
                        onReplaceCover = onReplaceCover,
                        onGenerateCoverClick = onGenerateCoverClick,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverTitleTopSpacing.dp))
                    Text(
                        text = title.ifBlank { book.metadata.title },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramondFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverAuthorSpacing.dp))
                    Text(
                        text = authors.splitValues().firstOrNull()
                            ?: stringResource(R.string.book_unknown_author),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(tuning.detailsTabletCoverStatusSpacing.dp))
                    StatusPill(
                        status = book.book.status,
                        syncStatus = book.book.syncStatus,
                    )
                }
                Spacer(modifier = Modifier.height(tuning.detailsMobileBottomPadding.dp))

                TabletRightHeader(
                    aiTextAvailable = aiTextAvailable,
                    onAutofillClick = onAutofillClick,
                )
                EditorialDivider(modifier = Modifier.fillMaxWidth(), horizontalPadding = 0.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = tuning.detailsPaneVerticalPadding.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TabletMetadataField(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        label = stringResource(R.string.edit_metadata_field_title),
                    ) {
                        MetadataTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            label = stringResource(R.string.edit_metadata_field_title),
                            isError = titleError,
                            supportingText = if (titleError) stringResource(R.string.edit_metadata_required) else null,
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.AutoMirrored.Outlined.ShortText,
                        label = stringResource(R.string.edit_metadata_field_subtitle),
                    ) {
                        MetadataTextField(
                            value = subtitle,
                            onValueChange = onSubtitleChange,
                            label = stringResource(R.string.edit_metadata_field_subtitle),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.PersonOutline,
                        label = stringResource(R.string.edit_metadata_field_authors),
                    ) {
                        MetadataTextField(
                            value = authors,
                            onValueChange = onAuthorsChange,
                            label = stringResource(R.string.edit_metadata_field_authors),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.BookmarkBorder,
                        label = stringResource(R.string.edit_metadata_field_series),
                    ) {
                        MetadataTextField(
                            value = seriesName,
                            onValueChange = onSeriesNameChange,
                            label = stringResource(R.string.edit_metadata_field_series),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Numbers,
                        label = stringResource(R.string.edit_metadata_field_series_index),
                    ) {
                        MetadataTextField(
                            value = seriesIndex,
                            onValueChange = onSeriesIndexChange,
                            label = stringResource(R.string.edit_metadata_field_series_index),
                            showLabel = false,
                            singleLine = true,
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Language,
                        label = stringResource(R.string.edit_metadata_field_language),
                    ) {
                        MetadataTextField(
                            value = language,
                            onValueChange = onLanguageChange,
                            label = stringResource(R.string.edit_metadata_field_language),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Business,
                        label = stringResource(R.string.edit_metadata_field_publisher),
                    ) {
                        MetadataTextField(
                            value = publisher,
                            onValueChange = onPublisherChange,
                            label = stringResource(R.string.edit_metadata_field_publisher),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.details_published_year),
                    ) {
                        MetadataTextField(
                            value = publishedDate,
                            onValueChange = onPublishedDateChange,
                            label = stringResource(R.string.edit_metadata_field_published_date),
                            showLabel = false,
                            singleLine = true,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.LocalOffer,
                        label = stringResource(R.string.edit_metadata_field_tags),
                    ) {
                        MetadataTextField(
                            value = tags,
                            onValueChange = onTagsChange,
                            label = stringResource(R.string.edit_metadata_field_tags),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                    TabletMetadataField(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.edit_metadata_field_isbn),
                    ) {
                        MetadataTextField(
                            value = isbn,
                            onValueChange = onIsbnChange,
                            label = stringResource(R.string.edit_metadata_field_isbn),
                            showLabel = false,
                            singleLine = true,
                        )
                    }
                }

                TabletSyncSection(book = book)
                TabletAboutSection(description = description, onEdit = onEditDescription)
                BookFooterActions(
                    onOriginalFiles = onOriginalFiles,
                    onDelete = onDelete,
                    enabled = originalFilesEnabled,
                )
            }
        }

        if (showTopChrome) {
            EditTopChrome(
                hazeState = hazeState,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topChromeHeightPx = it.height },
            )
        }
    }
}

@Composable
fun EditMetadataTopChromeOverlay(
    hazeState: HazeState,
    tabletWorkspace: Boolean,
    aiTextAvailable: Boolean,
    onBack: () -> Unit,
    onAutofillClick: () -> Unit,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
) {
    if (tabletWorkspace) {
        TabletEditTopChrome(
            hazeState = hazeState,
            aiTextAvailable = aiTextAvailable,
            onBack = onBack,
            onAutofillClick = onAutofillClick,
            modifier = modifier,
            drawBackground = drawBackground,
        )
    } else {
        EditTopChrome(
            hazeState = hazeState,
            onBack = onBack,
            modifier = modifier,
            drawBackground = drawBackground,
        )
    }
}

@Composable
private fun TabletEditTopChrome(
    hazeState: HazeState,
    aiTextAvailable: Boolean,
    onBack: () -> Unit,
    onAutofillClick: () -> Unit,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
) {
    val chromeTint = MaterialTheme.colorScheme.background
    val tuning = LocalVisualTuning.current

    Box(modifier = modifier.height(tuning.topChromeHeaderHeight.dp)) {
        if (drawBackground) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .hazeEffect(state = hazeState) {
                        inputScale = HazeInputScale.Fixed(0.5f)
                        blurEffect {
                            blurRadius = 40.dp
                            colorEffects = listOf(
                                HazeColorEffect.tint(chromeTint.copy(alpha = 0.68f)),
                            )
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f,
                            )
                        }
                    }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to chromeTint.copy(alpha = 0.98f),
                                0.56f to chromeTint.copy(alpha = 0.88f),
                                0.86f to chromeTint.copy(alpha = 0.42f),
                                1f to Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(TabletEditLeftPaneWeight)
                    .fillMaxWidth(),
            ) {
                TabletLeftTopBar(onBack = onBack)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
            )
            Box(
                modifier = Modifier
                    .weight(TabletEditRightPaneWeight)
                    .fillMaxWidth(),
            ) {
                TabletRightHeader(
                    aiTextAvailable = aiTextAvailable,
                    onAutofillClick = onAutofillClick,
                    modifier = Modifier.padding(horizontal = tuning.detailsPaneHorizontalPadding.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = tuning.topChromeDividerHalfHeight.dp)
                .fillMaxWidth(),
        ) {
            EditorialDivider(
                modifier = Modifier
                    .weight(TabletEditLeftPaneWeight)
                    .fillMaxWidth(),
            )
            Spacer(modifier = Modifier.width(1.dp))
            EditorialDivider(
                modifier = Modifier
                    .weight(TabletEditRightPaneWeight)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditTopChrome(
    hazeState: HazeState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
) {
    val chromeTint = MaterialTheme.colorScheme.background
    val tuning = LocalVisualTuning.current
    val chromeModifier = if (drawBackground) {
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Fixed(0.5f)
                blurEffect {
                    blurRadius = 40.dp
                    colorEffects = listOf(
                        HazeColorEffect.tint(chromeTint.copy(alpha = 0.68f)),
                    )
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                }
            }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to chromeTint.copy(alpha = 0.98f),
                        0.56f to chromeTint.copy(alpha = 0.88f),
                        0.86f to chromeTint.copy(alpha = 0.42f),
                        1f to Color.Transparent,
                    ),
                ),
            )
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = modifier) {
        Column(
            modifier = chromeModifier,
        ) {
            TabletLeftTopBar(onBack = onBack)
        }
        EditorialDivider(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = tuning.topChromeDividerHalfHeight.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun TabletLeftTopBar(onBack: () -> Unit) {
    val tuning = LocalVisualTuning.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tuning.topChromeHeaderHeight.dp)
            .padding(start = 18.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_back),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.edit_metadata_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(76.dp))
    }
}

@Composable
private fun TabletRightHeader(
    aiTextAvailable: Boolean,
    onAutofillClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionColor = MaterialTheme.colorScheme.primary
    val tuning = LocalVisualTuning.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(tuning.topChromeHeaderHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.edit_metadata_section),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clickable(onClick = onAutofillClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = actionColor,
            )
            Text(
                text = if (aiTextAvailable) {
                    stringResource(R.string.edit_metadata_autofill)
                } else {
                    stringResource(R.string.metadata_lookup_button)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = actionColor,
            )
        }
    }
}

@Composable
private fun EditBookCover(
    book: LibraryBook,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val coverPath = listOfNotNull(book.book.coverImagePath, book.book.coverThumbPath)
        .firstOrNull { it.isReadableCoverFile() }
    val coverAspectRatio = remember(coverPath) { coverPath.coverAspectRatio() }
    // Tapping the cover opens the book, as on a shelf.
    val openLabel = stringResource(R.string.reader_open)
    val readClick = Modifier.clickable(
        enabled = enabled,
        onClickLabel = openLabel,
        role = Role.Button,
        onClick = onClick,
    )

    if (coverPath != null) {
        Box(
            modifier = modifier
                .aspectRatio(coverAspectRatio)
                .shadow(6.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .then(readClick),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = stringResource(
                    R.string.cover_content_description,
                    book.metadata.title,
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            modifier = modifier
                .aspectRatio(DefaultCoverAspectRatio)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(readClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.cover_placeholder),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoverActions(
    aiCapabilities: AiCapabilities,
    onReplaceCover: () -> Unit,
    onGenerateCoverClick: () -> Unit,
) {
    val tuning = LocalVisualTuning.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tuning.detailsTabletCoverButtonSpacing.dp),
    ) {
        CoverActionButton(
            icon = Icons.Outlined.Image,
            label = stringResource(R.string.details_action_replace_cover),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
            onClick = onReplaceCover,
            modifier = Modifier.weight(1f),
        )
        if (aiCapabilities.hasCoverTools) {
            CoverActionButton(
                icon = Icons.Outlined.AutoAwesome,
                label = stringResource(
                    if (aiCapabilities.canGenerateCovers) R.string.details_action_generate_cover else R.string.details_action_find_cover,
                ),
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
                onClick = onGenerateCoverClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CoverActionButton(
    icon: ImageVector,
    label: String,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tuning = LocalVisualTuning.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(tuning.detailsTabletCoverButtonHeight.dp),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookFooterActions(
    onOriginalFiles: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onOriginalFiles, enabled = enabled) {
            Text(
                text = stringResource(R.string.original_files_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        TextButton(onClick = onDelete, enabled = enabled) {
            Text(
                text = stringResource(R.string.details_action_delete_book),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TabletMetadataField(
    icon: ImageVector,
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun TabletSyncSection(book: LibraryBook) {
    val tuning = LocalVisualTuning.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = tuning.detailsPaneVerticalPadding.dp + 4.dp,
            ),
    ) {
        Text(
            text = stringResource(R.string.edit_metadata_sync_section),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(14.dp))
        EditorialDivider(
            modifier = Modifier.fillMaxWidth(),
            horizontalPadding = 0.dp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        TabletInfoRow(
            icon = Icons.Outlined.CloudDone,
            label = stringResource(R.string.details_status),
            value = stringResource(when (book.book.syncStatus) {
                com.foliore.shared.domain.SyncStatus.SYNCED -> R.string.status_synced
                com.foliore.shared.domain.SyncStatus.LOCAL_ONLY -> R.string.status_local_only
                com.foliore.shared.domain.SyncStatus.PENDING_UPLOAD -> R.string.status_pending_upload
                com.foliore.shared.domain.SyncStatus.PENDING_DOWNLOAD -> R.string.status_pending_download
                com.foliore.shared.domain.SyncStatus.CONFLICT -> R.string.status_conflict
                else -> R.string.status_sync_error
            }),
            valueColor = MaterialTheme.colorScheme.secondary,
        )
        TabletInfoRow(
            icon = Icons.Outlined.Sync,
            label = stringResource(R.string.details_last_synced),
            value = book.book.lastSyncedAt.toTabletTimestamp()
                ?: stringResource(R.string.details_not_available),
        )
        TabletInfoRow(
            icon = Icons.Outlined.Description,
            label = stringResource(R.string.edit_metadata_revision),
            value = book.book.updatedAt.toTabletTimestamp()
                ?: stringResource(R.string.details_not_available),
        )
        TabletInfoRow(
            icon = Icons.Outlined.Folder,
            label = stringResource(R.string.edit_metadata_location),
            value = stringResource(R.string.edit_metadata_location_value, book.metadata.title),
        )
    }
}

@Composable
private fun TabletAboutSection(description: String?, onEdit: () -> Unit) {
    val descriptionText = remember(description) {
        description.toDisplayDescription()
    } ?: stringResource(R.string.book_no_description)
    val tuning = LocalVisualTuning.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = tuning.detailsPaneVerticalPadding.dp + 4.dp,
            ),
    ) {
        EditorialDivider(
            modifier = Modifier.fillMaxWidth(),
            horizontalPadding = 0.dp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.details_about_book),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CormorantGaramondFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit_metadata_description_edit),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = descriptionText,
            modifier = Modifier.clickable(
                onClickLabel = stringResource(R.string.edit_metadata_description_edit),
                onClick = onEdit,
            ),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                lineHeight = 27.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun DescriptionEditorDialog(
    description: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Edit readable text; HTML from the EPUB is kept unless the text actually changes.
    val initialText = remember { description.toDisplayDescription().orEmpty() }
    var text by rememberSaveable { mutableStateOf(initialText) }
    FolioreOptionsDialog(
        title = stringResource(R.string.details_about_book),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.edit_metadata_field_description)) },
                minLines = 6,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(5.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.edit_metadata_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (text.trim() == initialText.trim()) onDismiss() else onApply(text.trim())
                    },
                    shape = RoundedCornerShape(5.dp),
                ) {
                    Text(stringResource(R.string.batch_dialog_apply))
                }
            }
        }
    }
}

@Composable
private fun TabletInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataAutofillDialog(
    prompt: String,
    onPromptChange: (String) -> Unit,
    preview: MetadataAutofillPreview?,
    aiTextAvailable: Boolean,
    usesChatGptPlan: Boolean,
    lookupCandidates: List<MetadataLookupCandidate>,
    selectedLookupCandidateId: String?,
    isLookingUpMetadata: Boolean,
    isResolvingCandidate: Boolean,
    isGenerating: Boolean,
    generationStartedAt: Long?,
    generationError: AiPreviewFailure?,
    progress: SyncProgress?,
    onLookupMetadata: () -> Unit,
    onSelectLookupCandidate: (String) -> Unit,
    onApplyLookupCandidate: (MetadataLookupCandidate, Set<MetadataField>) -> Unit,
    onGeneratePreview: () -> Unit,
    onApplyPreview: (MetadataAutofillPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCandidateChoices by remember { mutableStateOf(false) }
    var selectedFields by rememberSaveable { mutableStateOf(MetadataField.defaultSelection) }
    var showPromptEditor by rememberSaveable { mutableStateOf(false) }
    var showDataNotice by rememberSaveable { mutableStateOf(false) }
    // With ChatGPT or an API key set up, AI is the first tab and opens selected.
    var selectedSourceName by rememberSaveable(aiTextAvailable) {
        mutableStateOf(if (aiTextAvailable) AutofillSource.AI.name else AutofillSource.OPEN_METADATA.name)
    }
    val selectedSource = if (aiTextAvailable) {
        runCatching { AutofillSource.valueOf(selectedSourceName) }
            .getOrDefault(AutofillSource.AI)
    } else {
        AutofillSource.OPEN_METADATA
    }
    // Search the open sources only once their tab is shown, so the AI tab never waits on it.
    var lookupStarted by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSource) {
        if (selectedSource == AutofillSource.OPEN_METADATA && !lookupStarted) {
            lookupStarted = true
            onLookupMetadata()
        }
    }
    val selectedLookupCandidate = lookupCandidates.firstOrNull { it.id == selectedLookupCandidateId }
        ?: lookupCandidates.firstOrNull()

    if (showPromptEditor) {
        MetadataPromptEditorDialog(
            prompt = prompt,
            onSave = { edited ->
                onPromptChange(edited)
                showPromptEditor = false
            },
            onDismiss = { showPromptEditor = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < 480.dp
            val horizontalMargin = if (compact) 12.dp else 24.dp
            val verticalMargin = if (maxHeight < 720.dp) 12.dp else 24.dp
            val dialogMaxHeight = (maxHeight - verticalMargin * 2).coerceAtMost(760.dp)
            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalMargin, vertical = verticalMargin)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = if (compact) 20.dp else 24.dp, vertical = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (aiTextAvailable) {
                                stringResource(R.string.ai_metadata_dialog_title)
                            } else {
                                stringResource(R.string.metadata_lookup_button)
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.offset(x = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    EditorialDivider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    if (aiTextAvailable) {
                        AutofillSourceSelector(
                            selectedSource = selectedSource,
                            onSelectSource = { selectedSourceName = it.name },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (selectedSource) {
                            AutofillSource.OPEN_METADATA -> {
                                if (isLookingUpMetadata) {
                                    AiDialogProgress(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                }

                                DialogSectionLabel(stringResource(R.string.metadata_lookup_section_title))
                                Spacer(modifier = Modifier.height(6.dp))
                                if (lookupCandidates.isEmpty() && !isLookingUpMetadata) {
                                    Text(
                                        text = stringResource(R.string.metadata_lookup_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (lookupCandidates.isNotEmpty()) {
                                    Box {
                                        TextButton(onClick = { showCandidateChoices = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.metadata_lookup_choose_result, lookupCandidates.size))
                                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                                        }
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showCandidateChoices,
                                            onDismissRequest = { showCandidateChoices = false },
                                            modifier = Modifier.heightIn(max = 420.dp),
                                        ) {
                                            lookupCandidates.forEach { candidate ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Column {
                                                        Text(candidate.metadata.title, style = MaterialTheme.typography.titleSmall)
                                                        Text(listOfNotNull(candidate.sourceName, candidate.metadata.publisher,
                                                            candidate.metadata.publishedDate).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                                    } },
                                                    onClick = {
                                                        selectedFields = selectedFields - MetadataField.COVER
                                                        onSelectLookupCandidate(candidate.id)
                                                        showCandidateChoices = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    selectedLookupCandidate?.let { candidate ->
                                        MetadataLookupCandidateCard(candidate, selected = true, onClick = { showCandidateChoices = true })
                                    }
                                    Spacer(modifier = Modifier.height(18.dp))
                                    DialogSectionLabel(stringResource(R.string.ai_metadata_preview_label))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (isResolvingCandidate) {
                                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    selectedLookupCandidate?.let { candidate ->
                                        MetadataFieldSelection(candidate, selectedFields) { field ->
                                            selectedFields = if (field in selectedFields) selectedFields - field else selectedFields + field
                                        }
                                    }
                                }
                            }

                            AutofillSource.AI -> {
                                DialogSectionLabel(stringResource(R.string.ai_metadata_preview_label))
                                Spacer(modifier = Modifier.height(10.dp))
                                when {
                                    isGenerating -> AiGeneratingStatus(startedAt = generationStartedAt)
                                    generationError != null -> {
                                        AiErrorMessage(
                                            message = noticeMessage(generationError.type),
                                            detail = generationError.detail
                                                ?.takeIf { generationError.type == LibraryNoticeType.AI_REQUEST_FAILED },
                                        )
                                        if (preview != null) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            MetadataPreviewContent(preview = preview)
                                        }
                                    }
                                    else -> MetadataPreviewContent(preview = preview)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    EditorialDivider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(14.dp))

                    when (selectedSource) {
                        AutofillSource.OPEN_METADATA -> {
                            val canApply = selectedLookupCandidate != null && !isLookingUpMetadata && !isResolvingCandidate &&
                                selectedFields.any { field -> if (field == MetadataField.COVER) selectedLookupCandidate.coverImagePath != null else field.available(selectedLookupCandidate.metadata) }
                            Button(
                                onClick = { selectedLookupCandidate?.let { onApplyLookupCandidate(it, selectedFields) } },
                                enabled = canApply,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.metadata_lookup_apply_selected)) }
                            if (lookupCandidates.isEmpty() && !isLookingUpMetadata) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = onLookupMetadata) {
                                        Text(stringResource(R.string.metadata_lookup_button_again))
                                    }
                                }
                            }
                        }

                        AutofillSource.AI -> {
                            if (preview != null && !isGenerating) {
                                Button(
                                    onClick = { onApplyPreview(preview) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.ai_metadata_apply_preview))
                                }
                            } else {
                                Button(
                                    onClick = onGeneratePreview,
                                    enabled = !isGenerating,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isGenerating) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = androidx.compose.material3.LocalContentColor.current,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isGenerating) {
                                            stringResource(R.string.ai_metadata_generating_preview)
                                        } else {
                                            stringResource(R.string.ai_metadata_generate_preview)
                                        },
                                    )
                                }
                            }
                            // Less used actions sit under the main button, wrapping instead of being cut off.
                            val linkPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = { showPromptEditor = true },
                                    enabled = !isGenerating,
                                    contentPadding = linkPadding,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(R.string.ai_edit_prompt), maxLines = 1)
                                }
                                if (preview != null && !isGenerating) {
                                    TextButton(
                                        onClick = onGeneratePreview,
                                        contentPadding = linkPadding,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(R.string.ai_metadata_regenerate_preview), maxLines = 1)
                                    }
                                }
                            }
                            AiDataNoticeToggle(
                                usesChatGptPlan = usesChatGptPlan,
                                expanded = showDataNotice,
                                onToggle = { showDataNotice = !showDataNotice },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * A small line saying which account pays for the request (the ChatGPT plan, as the plan usage
 * guidelines ask, or the API key). Tapping it shows what is sent to OpenAI.
 */
@Composable
private fun AiDataNoticeToggle(
    usesChatGptPlan: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = muted,
                )
                Text(
                    text = stringResource(
                        if (usesChatGptPlan) R.string.chatgpt_using_plan else R.string.ai_using_api_key,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = muted,
                )
            }
            if (usesChatGptPlan) {
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(com.foliore.android.ai.ChatGptOAuth.USAGE_SETTINGS_URL) } },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.chatgpt_manage_usage), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (expanded) {
            Text(
                text = stringResource(
                    if (usesChatGptPlan) R.string.ai_data_disclosure_chatgpt else R.string.ai_data_disclosure,
                ),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun MetadataPromptEditorDialog(
    prompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(prompt) }
    FolioreOptionsDialog(
        title = stringResource(R.string.ai_edit_prompt),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_metadata_prompt_label)) },
                minLines = 6,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(5.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.edit_metadata_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(text) },
                    enabled = text.isNotBlank(),
                    shape = RoundedCornerShape(5.dp),
                ) {
                    Text(stringResource(R.string.edit_metadata_save))
                }
            }
        }
    }
}

/** Indeterminate progress with the time so far, since a web-checked preview can take a minute. */
@Composable
private fun AiGeneratingStatus(startedAt: Long?) {
    var elapsedSeconds by remember(startedAt) { mutableStateOf(0L) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            elapsedSeconds = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1000
            kotlinx.coroutines.delay(1_000)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_progress_generating_metadata),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        )
        Text(
            text = stringResource(R.string.ai_metadata_generating_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Why an AI preview failed, with the provider's own message when the reason is not a known one. */
private data class AiPreviewFailure(val type: LibraryNoticeType, val detail: String?)

@Composable
private fun AiErrorMessage(message: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            detail?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AutofillSourceSelector(
    selectedSource: AutofillSource,
    onSelectSource: (AutofillSource) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AutofillSourceButton(
            label = stringResource(R.string.autofill_source_ai),
            selected = selectedSource == AutofillSource.AI,
            onClick = { onSelectSource(AutofillSource.AI) },
            modifier = Modifier.weight(1f),
        )
        AutofillSourceButton(
            label = stringResource(R.string.autofill_source_open_metadata),
            selected = selectedSource == AutofillSource.OPEN_METADATA,
            onClick = { onSelectSource(AutofillSource.OPEN_METADATA) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AutofillSourceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Narrow padding so labels such as "Open sources" fit on a phone without being cut off.
    val contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(5.dp),
            contentPadding = contentPadding,
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(5.dp),
            contentPadding = contentPadding,
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetadataLookupCandidateCard(
    candidate: MetadataLookupCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = Color.Transparent,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val coverPath = candidate.coverImagePath
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = candidate.metadata.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(DefaultCoverAspectRatio)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = candidate.metadata.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.metadata.authors.joinToString(", ")
                        .ifBlank { stringResource(R.string.book_unknown_author) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        candidate.metadata.language?.let { MetadataLanguages.display(it, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) },
                        candidate.metadata.publisher,
                        candidate.metadata.isbn,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = candidate.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    candidate.metadata.publishedDate.yearOnlyOrNull()?.let { year ->
                        Text(
                            text = year,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (candidate.coverImagePath != null) {
                        Text(
                            text = if (candidate.coverPixelWidth != null && candidate.coverPixelHeight != null) {
                                stringResource(
                                    R.string.metadata_lookup_cover_resolution,
                                    candidate.coverPixelWidth,
                                    candidate.coverPixelHeight,
                                )
                            } else {
                                stringResource(R.string.metadata_lookup_cover_available)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptEditorToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onToggle) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (expanded) {
                    stringResource(R.string.ai_hide_prompt)
                } else {
                    stringResource(R.string.ai_edit_prompt)
                },
            )
        }
    }
}

@Composable
private fun AiDialogProgress(
    progress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SyncProgressStatusLine(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        )
    }
}

@Composable
private fun MetadataPreviewContent(preview: MetadataAutofillPreview?) {
    if (preview == null) {
        Text(
            text = stringResource(R.string.ai_metadata_preview_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_title),
            value = preview.title,
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_subtitle),
            value = preview.subtitle.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_authors),
            value = preview.authors.joinToString(", "),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_sort_author),
            value = preview.sortAuthor.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_series),
            value = preview.seriesName.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_series_index),
            value = preview.seriesIndex.formatSeriesIndex(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_language),
            value = preview.language.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_publisher),
            value = preview.publisher.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_published_date),
            value = preview.publishedDate.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_tags),
            value = preview.tags.joinToString(", "),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_isbn),
            value = preview.isbn.orEmpty(),
        )
        PreviewValueRow(
            label = stringResource(R.string.edit_metadata_field_description),
            value = preview.description.orEmpty(),
            maxLines = 7,
        )
    }
}

@Composable
private fun PreviewValueRow(
    label: String,
    value: String,
    maxLines: Int = 2,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(118.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.ifBlank { stringResource(R.string.details_not_available) },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class CoverDialogMode { FIND, GENERATE }

@Composable
private fun CoverGenerationDialog(
    canGenerate: Boolean,
    canFind: Boolean,
    usesChatGptPlan: Boolean,
    foundOptions: List<AiGeneratedCoverOption>,
    selectedFoundOptionId: String?,
    isFinding: Boolean,
    onFind: () -> Unit,
    onSelectFoundOption: (String) -> Unit,
    onUseFound: () -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    seriesPromptReference: AiCoverPromptRecord?,
    options: List<AiGeneratedCoverOption>,
    selectedOptionId: String?,
    isGenerating: Boolean,
    progress: SyncProgress?,
    onGenerate: () -> Unit,
    onSelectOption: (String) -> Unit,
    onUseSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showReport by remember { mutableStateOf(false) }
    if (showReport) com.foliore.android.ui.library.ContentReportDialog { showReport = false }
    var showPromptEditor by rememberSaveable { mutableStateOf(false) }
    var modeName by rememberSaveable {
        mutableStateOf(if (canGenerate) CoverDialogMode.GENERATE.name else CoverDialogMode.FIND.name)
    }
    val mode = when {
        !canFind -> CoverDialogMode.GENERATE
        !canGenerate -> CoverDialogMode.FIND
        else -> CoverDialogMode.valueOf(modeName)
    }
    val isBusy = isGenerating || isFinding

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp)
                .heightIn(max = 780.dp)
                .padding(18.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (mode == CoverDialogMode.FIND) R.string.ai_cover_find_dialog_title else R.string.ai_cover_dialog_title,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.ai_cover_aspect_ratio),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                EditorialDivider(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(18.dp))

                if (canFind && canGenerate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AutofillSourceButton(
                            label = stringResource(R.string.ai_cover_mode_find),
                            selected = mode == CoverDialogMode.FIND,
                            onClick = { if (!isBusy) modeName = CoverDialogMode.FIND.name },
                            modifier = Modifier.weight(1f),
                        )
                        AutofillSourceButton(
                            label = stringResource(R.string.ai_cover_mode_generate),
                            selected = mode == CoverDialogMode.GENERATE,
                            onClick = { if (!isBusy) modeName = CoverDialogMode.GENERATE.name },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (isBusy) {
                    AiDialogProgress(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Finding covers uses the text provider (possibly the ChatGPT plan); generating always uses the API key.
                Text(
                    text = stringResource(
                        if (mode == CoverDialogMode.FIND && usesChatGptPlan) {
                            R.string.ai_data_disclosure_chatgpt
                        } else {
                            R.string.ai_data_disclosure
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mode == CoverDialogMode.FIND) {
                    if (usesChatGptPlan) ChatGptPlanIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.ai_cover_find_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.TextButton(onClick = { showReport = true }) {
                        Text(stringResource(R.string.report_ai_content))
                    }
                    PromptEditorToggle(
                        expanded = showPromptEditor,
                        onToggle = { showPromptEditor = !showPromptEditor },
                    )
                    if (showPromptEditor) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = onPromptChange,
                            label = { Text(text = stringResource(R.string.ai_cover_prompt_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            singleLine = false,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(5.dp),
                        )
                    }
                    if (seriesPromptReference != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(
                                R.string.ai_cover_series_reference,
                                seriesPromptReference.title,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                if (mode == CoverDialogMode.FIND) {
                    FoundCoverGrid(
                        options = foundOptions,
                        selectedOptionId = selectedFoundOptionId,
                        isFinding = isFinding,
                        onSelectOption = onSelectFoundOption,
                    )
                } else {
                    GeneratedCoverRow(
                        prompt = prompt,
                        options = options,
                        selectedOptionId = selectedOptionId,
                        isGenerating = isGenerating,
                        onSelectOption = onSelectOption,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.edit_metadata_cancel))
                    }
                    if (mode == CoverDialogMode.FIND) {
                        OutlinedButton(onClick = onFind, enabled = !isBusy) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isFinding -> stringResource(R.string.ai_cover_finding)
                                    foundOptions.isEmpty() -> stringResource(R.string.ai_cover_find)
                                    else -> stringResource(R.string.ai_cover_find_again)
                                },
                            )
                        }
                        Button(
                            onClick = onUseFound,
                            enabled = selectedFoundOptionId != null && foundOptions.isNotEmpty() && !isBusy,
                        ) {
                            Text(text = stringResource(R.string.ai_cover_use_selected))
                        }
                    } else {
                        OutlinedButton(
                            onClick = onGenerate,
                            enabled = !isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGenerating) {
                                    stringResource(R.string.ai_cover_generating)
                                } else if (options.isEmpty()) {
                                    stringResource(R.string.ai_cover_generate)
                                } else {
                                    stringResource(R.string.ai_cover_regenerate)
                                },
                            )
                        }
                        Button(
                            onClick = onUseSelected,
                            enabled = selectedOptionId != null && !isBusy,
                        ) {
                            Text(text = stringResource(R.string.ai_cover_use_selected))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedCoverRow(
    prompt: String,
    options: List<AiGeneratedCoverOption>,
    selectedOptionId: String?,
    isGenerating: Boolean,
    onSelectOption: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pendingStyle = if (isGenerating) {
            stringResource(R.string.ai_cover_option_generating)
        } else {
            stringResource(R.string.ai_cover_option_pending)
        }
        val displayedOptions = if (options.isEmpty()) {
            listOf(
                AiGeneratedCoverOption(
                    id = "pending-a",
                    label = stringResource(R.string.ai_cover_option_a),
                    style = pendingStyle,
                    prompt = prompt,
                    imagePath = null,
                ),
                AiGeneratedCoverOption(
                    id = "pending-b",
                    label = stringResource(R.string.ai_cover_option_b),
                    style = pendingStyle,
                    prompt = prompt,
                    imagePath = null,
                ),
            )
        } else {
            options
        }
        if (maxWidth >= 520.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                displayedOptions.forEach { option ->
                    CoverOptionCard(
                        option = option,
                        selected = selectedOptionId == option.id,
                        enabled = options.isNotEmpty(),
                        onClick = { onSelectOption(option.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                displayedOptions.forEach { option ->
                    CoverOptionCard(
                        option = option,
                        selected = selectedOptionId == option.id,
                        enabled = options.isNotEmpty(),
                        onClick = { onSelectOption(option.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FoundCoverGrid(
    options: List<AiGeneratedCoverOption>,
    selectedOptionId: String?,
    isFinding: Boolean,
    onSelectOption: (String) -> Unit,
) {
    if (options.isEmpty()) {
        if (!isFinding) {
            Text(
                text = stringResource(R.string.ai_cover_find_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            options.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { option ->
                        CoverOptionCard(
                            option = option,
                            selected = selectedOptionId == option.id,
                            enabled = true,
                            onClick = { onSelectOption(option.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Shown wherever a request uses the person's ChatGPT plan, as the plan usage guidelines ask. */
@Composable
private fun ChatGptPlanIndicator() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chatgpt_using_plan),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = { runCatching { uriHandler.openUri(com.foliore.android.ai.ChatGptOAuth.USAGE_SETTINGS_URL) } }) {
            Text(stringResource(R.string.chatgpt_manage_usage), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CoverOptionCard(
    option: AiGeneratedCoverOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }

    Column(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DefaultCoverAspectRatio),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        ) {
            if (option.imagePath != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = File(option.imagePath),
                        contentDescription = option.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (selected) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = stringResource(R.string.ai_cover_option_selected),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = option.style,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!option.isFound) {
            Text(
                text = option.style,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 20.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = stringResource(R.string.edit_metadata_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun MetadataTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    minLines: Int = 1,
    singleLine: Boolean = minLines == 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    showLabel: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (showLabel) {
            { Text(text = label) }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (showLabel) 56.dp else 48.dp),
        minLines = minLines,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text = text) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(5.dp),
    )
}

private enum class AutofillSource {
    OPEN_METADATA,
    AI,
}

private data class MetadataAutofillPreview(
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val sortAuthor: String?,
    val language: String?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
    val tags: List<String>,
    val isbn: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
)

private fun BookMetadataDraft.toAutofillPreview(): MetadataAutofillPreview {
    val cleanAuthors = authors.mapNotNull { it.cleanOptional() }
    return MetadataAutofillPreview(
        title = title.trim(),
        subtitle = subtitle?.cleanOptional(),
        authors = cleanAuthors,
        sortAuthor = sortAuthor?.cleanOptional() ?: cleanAuthors.firstOrNull()?.toSortAuthor(),
        language = language?.cleanOptional(),
        publisher = publisher?.cleanOptional(),
        publishedDate = publishedDate.yearOnlyOrNull(),
        description = description.toDisplayDescription(),
        tags = tags.mapNotNull { it.cleanOptional() }.distinctBy { it.lowercase() },
        isbn = isbn?.cleanOptional(),
        seriesName = seriesName?.cleanOptional(),
        seriesIndex = seriesIndex,
    )
}

private fun LibraryBook.toMetadataDraft(): BookMetadataDraft {
    return BookMetadataDraft(
        title = metadata.title,
        subtitle = metadata.subtitle?.cleanOptional(),
        authors = metadata.authors.mapNotNull { it.cleanOptional() },
        sortAuthor = metadata.sortAuthor?.cleanOptional(),
        language = metadata.language?.cleanOptional(),
        publisher = metadata.publisher?.cleanOptional(),
        publishedDate = metadata.publishedDate?.cleanOptional(),
        description = metadata.description?.cleanOptional(),
        tags = metadata.tags.mapNotNull { it.cleanOptional() },
        isbn = metadata.isbn?.cleanOptional(),
        identifiers = metadata.identifiers,
        seriesName = metadata.seriesName?.cleanOptional(),
        seriesIndex = metadata.seriesIndex,
    )
}

private fun List<AiCoverPromptRecord>.coverPromptReferenceFor(book: LibraryBook): AiCoverPromptRecord? {
    val seriesName = book.metadata.seriesName?.cleanOptional()
    val latestFirst = sortedByDescending { it.selectedAtEpochMillis }

    if (seriesName != null) {
        latestFirst.firstOrNull { record ->
            record.bookId != book.book.id &&
                record.seriesName?.equals(seriesName, ignoreCase = true) == true
        }?.let { return it }
    }

    return latestFirst.firstOrNull { it.bookId == book.book.id }
}

private fun String.cleanOptional(): String? = trim().takeIf { it.isNotEmpty() }

private fun String.splitValues(): List<String> {
    return split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun String?.toTabletTimestamp(): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val compact = raw.substringBefore('.').replace('T', ' ')
    return if (compact.length >= 16) compact.take(16) else compact
}

private fun String?.toDisplayDescription(): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val plain = if (HtmlMarkup.containsMatchIn(raw)) {
        HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    } else {
        raw
    }.replace('\u00A0', ' ')
    return plain.lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun String?.yearOnlyOrNull(): String? {
    val raw = this?.cleanOptional() ?: return null
    return Regex("\\b(\\d{4})\\b").find(raw)?.groupValues?.get(1) ?: raw
}

private fun String.toSortAuthor(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}"
    } else {
        trim()
    }
}

private fun Double?.formatSeriesIndex(): String {
    val value = this ?: return ""
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

private fun String?.coverAspectRatio(): Float {
    if (this == null) return DefaultCoverAspectRatio

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(this, options)
    val width = options.outWidth
    val height = options.outHeight

    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        DefaultCoverAspectRatio
    }
}

private fun String.isReadableCoverFile(): Boolean {
    if (!File(this).isFile) return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(this, options)
    return options.outWidth > 0 && options.outHeight > 0
}

private const val DefaultCoverAspectRatio = 2f / 3f
private val HtmlMarkup = Regex("<[a-zA-Z/!][^>]*>|&[a-zA-Z#][a-zA-Z0-9]*;")
