package com.foliore.android.ui.details

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.StatusPill
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File

@Composable
fun EditMetadataScreen(
    book: LibraryBook?,
    onBack: () -> Unit,
    onSave: (BookMetadataDraft) -> Unit,
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean,
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
    var seriesIndex by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.seriesIndex?.toString().orEmpty()) }
    var language by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.language.orEmpty()) }
    var publisher by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publisher.orEmpty()) }
    var publishedDate by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.publishedDate.orEmpty()) }
    var description by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.description.orEmpty()) }
    var tags by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.tags.joinToString(", ")) }
    var isbn by rememberSaveable(book.book.id) { mutableStateOf(book.metadata.isbn.orEmpty()) }
    var titleTouched by remember { mutableStateOf(false) }
    val titleError = titleTouched && title.isBlank()
    val saveDraft = {
        titleTouched = true
        if (title.isNotBlank()) {
            onSave(
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
                ),
            )
        }
    }

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
                tags = tags,
                onTagsChange = { tags = it },
                isbn = isbn,
                onIsbnChange = { isbn = it },
                description = description,
                onBack = onBack,
                onSave = saveDraft,
                onPrepareEpub = onPrepareEpub,
                onShareExport = onShareExport,
                onSyncNow = onSyncNow,
                isSyncing = isSyncing,
                onLibraryClick = onLibraryClick,
                onSettingsClick = onSettingsClick,
            )
        } else {
            PhoneEditMetadataContent(
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
                onBack = onBack,
                onSave = saveDraft,
            )
        }
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
    tags: String,
    onTagsChange: (String) -> Unit,
    isbn: String,
    onIsbnChange: (String) -> Unit,
    description: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean,
    onLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
                    Spacer(modifier = Modifier.height(20.dp))
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
                isSyncing = isSyncing,
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
            TabletRightHeader()
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
                onPrepareEpub = onPrepareEpub,
                onShareExport = onShareExport,
                saveEnabled = title.isNotBlank(),
                compact = compactActions,
            )
        }
    }
}

@Composable
private fun PhoneEditMetadataContent(
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
            MetadataTextField(
                value = title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.edit_metadata_field_title),
                isError = titleError,
                supportingText = if (titleError) stringResource(R.string.edit_metadata_required) else null,
            )
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
private fun TabletRightHeader() {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.edit_metadata_autofill),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
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
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    saveEnabled: Boolean,
    compact: Boolean,
) {
    val prepareLabel = if (compact) {
        stringResource(R.string.edit_metadata_prepare_short)
    } else {
        stringResource(R.string.details_action_prepare_epub)
    }
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
        OutlinedButton(
            onClick = onPrepareEpub,
            modifier = Modifier
                .weight(1.12f)
                .height(56.dp),
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = prepareLabel,
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
    isSyncing: Boolean,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                enabled = !isSyncing,
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
