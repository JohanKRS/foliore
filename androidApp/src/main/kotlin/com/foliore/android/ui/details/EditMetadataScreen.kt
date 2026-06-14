package com.foliore.android.ui.details

import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.android.ai.AiCoverStyleRequest
import com.foliore.android.ai.AiGeneratedCoverOption
import com.foliore.android.ai.AiProviderMode
import com.foliore.android.ai.isEnabled
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.StatusPill
import com.foliore.android.ui.library.SyncProgressDivider
import com.foliore.android.ui.library.SyncProgressStatusLine
import com.foliore.android.viewmodel.SyncProgress
import com.foliore.android.viewmodel.SyncProgressPhase
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File

@Composable
fun EditMetadataScreen(
    book: LibraryBook?,
    aiProviderMode: AiProviderMode,
    aiCoverPromptRecords: List<AiCoverPromptRecord>,
    onBack: () -> Unit,
    onSave: (BookMetadataDraft) -> Unit,
    onShareExport: () -> Unit,
    onAutofillClick: () -> Unit,
    onCoverGenerationUnavailable: () -> Unit,
    onGenerateMetadataPreview: (String, BookMetadataDraft, (BookMetadataDraft) -> Unit) -> Unit,
    onGenerateCoverImages: (
        String,
        BookMetadataDraft,
        AiCoverPromptRecord?,
        List<AiCoverStyleRequest>,
        (List<AiGeneratedCoverOption>) -> Unit,
    ) -> Unit,
    onUseGeneratedCover: (AiGeneratedCoverOption, AiCoverPromptRecord, () -> Unit) -> Unit,
    onReplaceCover: () -> Unit,
    onSyncNow: () -> Unit,
    isLoading: Boolean,
    progress: SyncProgress?,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
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

    var title by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.title) }
    var subtitle by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.subtitle.orEmpty()) }
    var authors by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.authors.joinToString(", ")) }
    var sortAuthor by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.sortAuthor.orEmpty()) }
    var seriesName by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.seriesName.orEmpty()) }
    var seriesIndex by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.seriesIndex.formatSeriesIndex()) }
    var language by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.language.orEmpty()) }
    var publisher by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publisher.orEmpty()) }
    var publishedDate by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publishedDate.orEmpty()) }
    var description by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.description.orEmpty()) }
    var tags by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.tags.joinToString(", ")) }
    var isbn by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.isbn.orEmpty()) }
    var titleTouched by remember { mutableStateOf(false) }
    var showMetadataAutofillDialog by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var showCoverGenerationDialog by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var metadataPreview by remember(book.book.id) { mutableStateOf<MetadataAutofillPreview?>(null) }
    var coverOptions by remember(book.book.id) { mutableStateOf<List<AiGeneratedCoverOption>>(emptyList()) }
    var selectedCoverOptionId by rememberSaveable(book.book.id) { mutableStateOf<String?>(null) }
    var coverGenerationRound by rememberSaveable(book.book.id) { mutableStateOf(0) }
    val defaultMetadataPrompt = stringResource(R.string.ai_metadata_default_prompt)
    val defaultCoverPrompt = stringResource(R.string.ai_cover_default_prompt)
    var metadataPrompt by rememberSaveable(book.book.id) { mutableStateOf(defaultMetadataPrompt) }
    var coverPrompt by rememberSaveable(book.book.id) { mutableStateOf(defaultCoverPrompt) }
    val seriesPromptReference = remember(book.book.id, book.metadata.seriesName, aiCoverPromptRecords) {
        aiCoverPromptRecords.coverPromptReferenceFor(book)
    }
    val titleError = titleTouched && title.isBlank()
    val isGeneratingMetadata = isLoading && progress?.phase == SyncProgressPhase.GENERATING_METADATA
    val isGeneratingCovers = isLoading && progress?.phase == SyncProgressPhase.GENERATING_COVERS
    val currentDraft = {
        BookMetadataDraft(
            title = title,
            subtitle = subtitle.cleanOptional(),
            authors = authors.splitValues(),
            sortAuthor = sortAuthor.cleanOptional(),
            language = language.cleanOptional(),
            publisher = publisher.cleanOptional(),
            publishedDate = publishedDate.cleanOptional(),
            description = description.cleanOptional(),
            tags = tags.splitValues(),
            isbn = isbn.cleanOptional(),
            identifiers = book.metadata.identifiers,
            seriesName = seriesName.cleanOptional(),
            seriesIndex = seriesIndex.cleanOptional()?.toDoubleOrNull(),
        )
    }
    val saveDraft = {
        titleTouched = true
        if (title.isNotBlank()) {
            onSave(currentDraft())
        }
    }
    val applyMetadataPreview: (MetadataAutofillPreview) -> Unit = { preview ->
        title = preview.title
        subtitle = preview.subtitle.orEmpty()
        authors = preview.authors.joinToString(", ")
        sortAuthor = preview.sortAuthor.orEmpty()
        seriesName = preview.seriesName.orEmpty()
        seriesIndex = preview.seriesIndex.formatSeriesIndex()
        language = preview.language.orEmpty()
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

        if (tabletWorkspace) {
            TabletEditBookScreen(
                book = book,
                compactActions = maxWidth < maxHeight,
                title = title,
                onTitleChange = {
                    title = it
                    titleTouched = true
                },
                titleError = titleError,
                authors = authors,
                onAuthorsChange = { authors = it },
                seriesName = seriesName,
                onSeriesNameChange = { seriesName = it },
                seriesIndex = seriesIndex,
                onSeriesIndexChange = { seriesIndex = it },
                language = language,
                onLanguageChange = { language = it },
                publishedDate = publishedDate,
                onPublishedDateChange = { publishedDate = it },
                tags = tags,
                onTagsChange = { tags = it },
                isbn = isbn,
                onIsbnChange = { isbn = it },
                description = description,
                onBack = onBack,
                onSave = saveDraft,
                onShareExport = onShareExport,
                onReplaceCover = onReplaceCover,
                onGenerateCoverClick = { showCoverGenerationDialog = true },
                onSyncNow = onSyncNow,
                isLoading = isLoading,
                progress = progress,
                onLibraryClick = onLibraryClick,
                onSettingsClick = onSettingsClick,
                aiProviderMode = aiProviderMode,
                onAutofillClick = { showMetadataAutofillDialog = true },
            )
        } else {
            PhoneEditMetadataContent(
                aiProviderMode = aiProviderMode,
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
                sortAuthor = sortAuthor,
                onSortAuthorChange = { sortAuthor = it },
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
                onDescriptionChange = { description = it },
                tags = tags,
                onTagsChange = { tags = it },
                isbn = isbn,
                onIsbnChange = { isbn = it },
                onAutofillClick = { showMetadataAutofillDialog = true },
                onGenerateCoverClick = { showCoverGenerationDialog = true },
                onReplaceCover = onReplaceCover,
                onBack = onBack,
                onSave = saveDraft,
            )
        }
    }

    if (showMetadataAutofillDialog) {
        MetadataAutofillDialog(
            prompt = metadataPrompt,
            onPromptChange = { metadataPrompt = it },
            preview = metadataPreview,
            aiProviderMode = aiProviderMode,
            isGenerating = isGeneratingMetadata,
            progress = progress,
            onGeneratePreview = {
                if (!isGeneratingMetadata) {
                    if (!aiProviderMode.isEnabled) {
                        onAutofillClick()
                    } else {
                        onGenerateMetadataPreview(metadataPrompt, currentDraft()) { previewDraft ->
                            metadataPreview = previewDraft.toAutofillPreview()
                        }
                    }
                }
            },
            onApplyPreview = applyMetadataPreview,
            onDismiss = { showMetadataAutofillDialog = false },
        )
    }

    if (showCoverGenerationDialog) {
        CoverGenerationDialog(
            prompt = coverPrompt,
            onPromptChange = { coverPrompt = it },
            seriesPromptReference = seriesPromptReference,
            options = coverOptions,
            selectedOptionId = selectedCoverOptionId,
            isGenerating = isGeneratingCovers,
            progress = progress,
            onGenerate = {
                if (!isGeneratingCovers) {
                    if (!aiProviderMode.isEnabled) {
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
    compactActions: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: Boolean,
    authors: String,
    onAuthorsChange: (String) -> Unit,
    seriesName: String,
    onSeriesNameChange: (String) -> Unit,
    seriesIndex: String,
    onSeriesIndexChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    publishedDate: String,
    onPublishedDateChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    isbn: String,
    onIsbnChange: (String) -> Unit,
    description: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShareExport: () -> Unit,
    onReplaceCover: () -> Unit,
    onGenerateCoverClick: () -> Unit,
    onSyncNow: () -> Unit,
    isLoading: Boolean,
    progress: SyncProgress?,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    aiProviderMode: AiProviderMode,
    onAutofillClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight(),
        ) {
            TabletLeftTopBar(onBack = onBack)
            EditorialDivider(modifier = Modifier.fillMaxWidth())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    EditBookCover(
                        book = book,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onReplaceCover,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.details_action_replace_cover),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onGenerateCoverClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.details_action_generate_cover),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title.ifBlank { book.metadata.title },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = authors.splitValues().firstOrNull()
                            ?: stringResource(R.string.book_unknown_author),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusPill(
                        status = book.book.status,
                        syncStatus = book.book.syncStatus,
                    )
                }
            }
            TabletEmbeddedBottomNav(
                progress = progress,
                isLoading = isLoading,
                onLibraryClick = onLibraryClick,
                onSyncClick = onSyncNow,
                onSettingsClick = onSettingsClick,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        )

        Column(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight(),
        ) {
            TabletRightHeader(
                aiProviderMode = aiProviderMode,
                onAutofillClick = onAutofillClick,
            )
            EditorialDivider(modifier = Modifier.fillMaxWidth())

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
                )
                TabletSyncSection(book = book)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
                )
                TabletAboutSection(description = description.ifBlank { book.metadata.description.orEmpty() })
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
            )
            TabletEditActions(
                onSave = onSave,
                onShareExport = onShareExport,
                saveEnabled = title.isNotBlank(),
                compact = compactActions,
            )
        }
    }
}

@Composable
private fun PhoneEditMetadataContent(
    aiProviderMode: AiProviderMode,
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: Boolean,
    subtitle: String,
    onSubtitleChange: (String) -> Unit,
    authors: String,
    onAuthorsChange: (String) -> Unit,
    sortAuthor: String,
    onSortAuthorChange: (String) -> Unit,
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
    onDescriptionChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    isbn: String,
    onIsbnChange: (String) -> Unit,
    onAutofillClick: () -> Unit,
    onGenerateCoverClick: () -> Unit,
    onReplaceCover: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EditTopBar(onBack = onBack)
        EditorialDivider(modifier = Modifier.fillMaxWidth())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_metadata_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = onAutofillClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (aiProviderMode.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.edit_metadata_autofill))
            }
            MetadataTextField(
                value = title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.edit_metadata_field_title),
                isError = titleError,
                supportingText = if (titleError) stringResource(R.string.edit_metadata_required) else null,
            )
            OutlinedButton(
                onClick = onReplaceCover,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.details_action_replace_cover))
            }
            OutlinedButton(
                onClick = onGenerateCoverClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.details_action_generate_cover))
            }
            MetadataTextField(
                value = subtitle,
                onValueChange = onSubtitleChange,
                label = stringResource(R.string.edit_metadata_field_subtitle),
            )
            MetadataTextField(
                value = authors,
                onValueChange = onAuthorsChange,
                label = stringResource(R.string.edit_metadata_field_authors),
                supportingText = stringResource(R.string.edit_metadata_field_authors_helper),
            )
            MetadataTextField(
                value = sortAuthor,
                onValueChange = onSortAuthorChange,
                label = stringResource(R.string.edit_metadata_field_sort_author),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataTextField(
                    value = seriesName,
                    onValueChange = onSeriesNameChange,
                    label = stringResource(R.string.edit_metadata_field_series),
                    modifier = Modifier.weight(1f),
                )
                MetadataTextField(
                    value = seriesIndex,
                    onValueChange = onSeriesIndexChange,
                    label = stringResource(R.string.edit_metadata_field_series_index),
                    modifier = Modifier.weight(0.72f),
                    keyboardType = KeyboardType.Decimal,
                )
            }
            MetadataTextField(
                value = language,
                onValueChange = onLanguageChange,
                label = stringResource(R.string.edit_metadata_field_language),
            )
            MetadataTextField(
                value = publisher,
                onValueChange = onPublisherChange,
                label = stringResource(R.string.edit_metadata_field_publisher),
            )
            MetadataTextField(
                value = publishedDate,
                onValueChange = onPublishedDateChange,
                label = stringResource(R.string.edit_metadata_field_published_date),
                keyboardType = KeyboardType.Number,
            )
            MetadataTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.edit_metadata_field_description),
                minLines = 4,
                singleLine = false,
            )
            MetadataTextField(
                value = tags,
                onValueChange = onTagsChange,
                label = stringResource(R.string.edit_metadata_field_tags),
                supportingText = stringResource(R.string.edit_metadata_field_tags_helper),
            )
            MetadataTextField(
                value = isbn,
                onValueChange = onIsbnChange,
                label = stringResource(R.string.edit_metadata_field_isbn),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.edit_metadata_cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.edit_metadata_save))
                }
            }
        }
    }
}

@Composable
private fun TabletLeftTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
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
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(76.dp))
    }
}

@Composable
private fun TabletRightHeader(
    aiProviderMode: AiProviderMode,
    onAutofillClick: () -> Unit,
) {
    val actionColor = if (aiProviderMode.isEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .padding(start = 28.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.edit_metadata_section),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clickable(onClick = onAutofillClick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
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
                text = stringResource(R.string.edit_metadata_autofill),
                style = MaterialTheme.typography.bodyMedium,
                color = actionColor,
            )
        }
    }
}

@Composable
private fun EditBookCover(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    val coverPath = book.book.coverImagePath ?: book.book.coverThumbPath
    val coverAspectRatio = remember(coverPath) { coverPath.coverAspectRatio() }

    if (coverPath != null) {
        Box(
            modifier = modifier
                .aspectRatio(coverAspectRatio)
                .shadow(6.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp)),
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_metadata_sync_section),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(14.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(18.dp))
        TabletInfoRow(
            icon = Icons.Outlined.CloudDone,
            label = stringResource(R.string.details_status),
            value = stringResource(R.string.edit_metadata_sync_connected),
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
private fun TabletAboutSection(description: String?) {
    val descriptionText = remember(description) {
        description.toDisplayDescription()
    } ?: stringResource(R.string.book_no_description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.details_about_book).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                lineHeight = 27.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        )
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
private fun TabletEditActions(
    onSave: () -> Unit,
    onShareExport: () -> Unit,
    saveEnabled: Boolean,
    compact: Boolean,
) {
    val exportLabel = if (compact) {
        stringResource(R.string.edit_metadata_export_short)
    } else {
        stringResource(R.string.details_action_share_export)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.edit_metadata_save),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onShareExport,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = exportLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TabletEmbeddedBottomNav(
    progress: SyncProgress?,
    isLoading: Boolean,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (isLoading) {
            SyncProgressStatusLine(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp, top = 8.dp, end = 42.dp, bottom = 6.dp),
            )
        }
        SyncProgressDivider(
            isActive = isLoading,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(94.dp)
                    .padding(horizontal = 42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabletNavItem(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    label = stringResource(R.string.nav_library),
                    selected = true,
                    onClick = onLibraryClick,
                )
                TabletNavItem(
                    icon = Icons.Outlined.Sync,
                    label = stringResource(R.string.nav_sync),
                    selected = false,
                    enabled = !isLoading,
                    onClick = onSyncClick,
                )
                TabletNavItem(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.nav_settings),
                    selected = false,
                    onClick = onSettingsClick,
                )
            }
        }
    }
}

@Composable
private fun TabletNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }.copy(alpha = if (enabled) 1f else 0.4f)

    Column(
        modifier = Modifier
            .width(72.dp)
            .height(62.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .background(if (selected) color else Color.Transparent),
        )
    }
}

@Composable
private fun MetadataAutofillDialog(
    prompt: String,
    onPromptChange: (String) -> Unit,
    preview: MetadataAutofillPreview?,
    aiProviderMode: AiProviderMode,
    isGenerating: Boolean,
    progress: SyncProgress?,
    onGeneratePreview: () -> Unit,
    onApplyPreview: (MetadataAutofillPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPromptEditor by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .heightIn(max = 760.dp)
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
                Text(
                    text = stringResource(R.string.ai_metadata_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                EditorialDivider(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(18.dp))

                if (isGenerating) {
                    AiDialogProgress(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
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
                        label = { Text(text = stringResource(R.string.ai_metadata_prompt_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(5.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.ai_metadata_preview_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataPreviewContent(preview = preview)

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.edit_metadata_cancel))
                    }
                    OutlinedButton(
                        onClick = onGeneratePreview,
                        enabled = !isGenerating,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isGenerating) {
                                stringResource(R.string.ai_metadata_generating_preview)
                            } else if (preview == null || !aiProviderMode.isEnabled) {
                                stringResource(R.string.ai_metadata_generate_preview)
                            } else {
                                stringResource(R.string.ai_metadata_regenerate_preview)
                            },
                        )
                    }
                    Button(
                        onClick = { preview?.let(onApplyPreview) },
                        enabled = preview != null && !isGenerating,
                    ) {
                        Text(text = stringResource(R.string.ai_metadata_apply_preview))
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
            maxLines = 1,
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

@Composable
private fun CoverGenerationDialog(
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
    var showPromptEditor by rememberSaveable { mutableStateOf(false) }

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
                        text = stringResource(R.string.ai_cover_dialog_title),
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

                if (isGenerating) {
                    AiDialogProgress(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(18.dp))
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

                Spacer(modifier = Modifier.height(20.dp))
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

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.edit_metadata_cancel))
                    }
                    OutlinedButton(
                        onClick = onGenerate,
                        enabled = !isGenerating,
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
                        enabled = selectedOptionId != null && !isGenerating,
                    ) {
                        Text(text = stringResource(R.string.ai_cover_use_selected))
                    }
                }
            }
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
        Text(
            text = option.style,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    val plain = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
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

private const val DefaultCoverAspectRatio = 2f / 3f
