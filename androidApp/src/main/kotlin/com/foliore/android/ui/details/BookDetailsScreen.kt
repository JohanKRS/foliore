package com.foliore.android.ui.details

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.StatusPill
import com.foliore.shared.domain.LibraryBook
import java.io.File

@Composable
fun BookDetailsScreen(
    book: LibraryBook?,
    onEditMetadata: () -> Unit,
    onReplaceCover: () -> Unit,
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    onDeleteBook: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (book == null) {
            DetailsTopBar(onBack = onBack)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.details_book_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 560.dp
            val contentMaxWidth = if (expanded) 720.dp else 560.dp
            val horizontalPadding = if (maxWidth >= contentMaxWidth + 56.dp) 0.dp else 28.dp
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = 18.dp,
                            bottom = 36.dp,
                        ),
                ) {
                    DetailsTopBar(onBack = onBack)
                    Spacer(modifier = Modifier.height(22.dp))
                    BookHeroSection(
                        book = book,
                        expanded = expanded,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    EditorialDivider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(20.dp))
                    DetailsActionList(
                        onEditMetadata = onEditMetadata,
                        onReplaceCover = onReplaceCover,
                        onPrepareEpub = onPrepareEpub,
                        onShareExport = onShareExport,
                        onDeleteBook = onDeleteBook,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    EditorialDivider(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(28.dp))
                    AboutBookSection(description = book.metadata.description)
                }
            }
        }
    }
}

@Composable
private fun DetailsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun BookHeroSection(
    book: LibraryBook,
    expanded: Boolean,
) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BookCover(
                book = book,
                modifier = Modifier.width(260.dp),
            )
            BookSummary(
                book = book,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            BookCover(
                book = book,
                modifier = Modifier.fillMaxWidth(),
            )
            BookSummary(book = book)
        }
    }
}

@Composable
private fun BookCover(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    val coverPath = book.book.coverImagePath ?: book.book.coverThumbPath
    val coverAspectRatio = remember(coverPath) { coverPath.coverAspectRatio() }

    if (coverPath != null) {
        Box(
            modifier = modifier
                .aspectRatio(coverAspectRatio)
                .shadow(5.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
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
                .clip(RoundedCornerShape(8.dp))
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
private fun BookSummary(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = book.metadata.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = book.metadata.authors.firstOrNull().orEmpty()
                .ifBlank { stringResource(R.string.book_unknown_author) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(18.dp))
        StatusPill(
            status = book.book.status,
            syncStatus = book.book.syncStatus,
        )
        Spacer(modifier = Modifier.height(26.dp))
        DetailsMetadataRow(
            label = stringResource(R.string.details_series),
            value = book.seriesDisplay() ?: stringResource(R.string.details_not_available),
        )
        DetailsMetadataRow(
            label = stringResource(R.string.details_language),
            value = book.metadata.language ?: stringResource(R.string.details_not_available),
        )
        DetailsMetadataRow(
            label = stringResource(R.string.details_publisher),
            value = book.metadata.publisher ?: stringResource(R.string.details_not_available),
        )
        DetailsMetadataRow(
            label = stringResource(R.string.details_file_format),
            value = stringResource(R.string.details_file_format_epub),
        )
        DetailsMetadataRow(
            label = stringResource(R.string.details_last_synced),
            value = book.book.lastSyncedAt.toDisplayTimestamp()
                ?: stringResource(R.string.details_not_available),
        )
    }
}

@Composable
private fun DetailsMetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(108.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun DetailsActionList(
    onEditMetadata: () -> Unit,
    onReplaceCover: () -> Unit,
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    onDeleteBook: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailsActionRow(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.details_action_edit_metadata),
            onClick = onEditMetadata,
        )
        DetailsRowDivider()
        DetailsActionRow(
            icon = Icons.Outlined.Image,
            label = stringResource(R.string.details_action_replace_cover),
            onClick = onReplaceCover,
        )
        DetailsRowDivider()
        DetailsActionRow(
            icon = Icons.Outlined.Description,
            label = stringResource(R.string.details_action_prepare_epub),
            onClick = onPrepareEpub,
        )
        DetailsRowDivider()
        DetailsActionRow(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.details_action_share_export),
            onClick = onShareExport,
        )
        DetailsRowDivider()
        DetailsActionRow(
            icon = Icons.Outlined.Delete,
            label = stringResource(R.string.details_action_delete_book),
            onClick = onDeleteBook,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DetailsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val contentColor = color ?: MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = contentColor.copy(alpha = if (color == null) 0.8f else 0.92f),
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = if (color == null) 0.88f else 0.94f),
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun DetailsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
    )
}

@Composable
private fun AboutBookSection(description: String?) {
    val descriptionText = remember(description) {
        description.toDisplayDescription()
    } ?: stringResource(R.string.book_no_description)

    Text(
        text = stringResource(R.string.details_about_book).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = descriptionText,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Serif,
            lineHeight = 28.sp,
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
    )
}

private fun LibraryBook.seriesDisplay(): String? {
    val name = metadata.seriesName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val index = metadata.seriesIndex?.let { value ->
        val asLong = value.toLong()
        if (value == asLong.toDouble()) asLong.toString() else value.toString()
    }
    return if (index == null) name else "$name #$index"
}

private fun String?.toDisplayTimestamp(): String? {
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
