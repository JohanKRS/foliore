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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        DetailsTopBar(onBack = onBack)
        EditorialDivider(modifier = Modifier.fillMaxWidth())

        if (book == null) {
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
            val expanded = maxWidth >= 760.dp
            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(42.dp),
                ) {
                    BookIdentityPanel(
                        book = book,
                        modifier = Modifier.weight(0.92f),
                    )
                    BookMetadataPanel(
                        book = book,
                        modifier = Modifier.weight(1.08f),
                        scrollable = true,
                        onEditMetadata = onEditMetadata,
                        onReplaceCover = onReplaceCover,
                        onPrepareEpub = onPrepareEpub,
                        onShareExport = onShareExport,
                        onDeleteBook = onDeleteBook,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    BookIdentityPanel(book = book)
                    BookMetadataPanel(
                        book = book,
                        scrollable = false,
                        onEditMetadata = onEditMetadata,
                        onReplaceCover = onReplaceCover,
                        onPrepareEpub = onPrepareEpub,
                        onShareExport = onShareExport,
                        onDeleteBook = onDeleteBook,
                    )
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
            .padding(start = 12.dp, top = 20.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.details_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun BookIdentityPanel(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    val coverPath = book.book.coverImagePath ?: book.book.coverThumbPath
    val coverAspectRatio = remember(coverPath) { coverPath.coverAspectRatio() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        if (coverPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
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
                modifier = Modifier
                    .fillMaxWidth()
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

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = book.metadata.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.metadata.authors.firstOrNull().orEmpty()
                .ifBlank { stringResource(R.string.book_unknown_author) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(14.dp))
        StatusPill(
            status = book.book.status,
            syncStatus = book.book.syncStatus,
        )
    }
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

@Composable
private fun BookMetadataPanel(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    scrollable: Boolean,
    onEditMetadata: () -> Unit,
    onReplaceCover: () -> Unit,
    onPrepareEpub: () -> Unit,
    onShareExport: () -> Unit,
    onDeleteBook: () -> Unit,
) {
    val panelModifier = if (scrollable) {
        modifier.verticalScroll(rememberScrollState())
    } else {
        modifier
    }
    val descriptionText = remember(book.metadata.description) {
        book.metadata.description.toDisplayDescription()
    } ?: stringResource(R.string.book_no_description)

    Column(
        modifier = panelModifier,
    ) {
        MetadataRow(
            label = stringResource(R.string.details_series),
            value = book.metadata.seriesName ?: stringResource(R.string.details_not_available),
        )
        MetadataRow(
            label = stringResource(R.string.details_language),
            value = book.metadata.language ?: stringResource(R.string.details_not_available),
        )
        MetadataRow(
            label = stringResource(R.string.details_publisher),
            value = book.metadata.publisher ?: stringResource(R.string.details_not_available),
        )
        MetadataRow(
            label = stringResource(R.string.details_file_format),
            value = stringResource(R.string.details_file_format_epub),
        )
        MetadataRow(
            label = stringResource(R.string.details_imported_at),
            value = book.book.importedAt,
        )
        Spacer(modifier = Modifier.height(24.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(18.dp))

        DetailsActionRow(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.details_action_edit_metadata),
            onClick = onEditMetadata,
        )
        DetailsActionRow(
            icon = Icons.Outlined.Image,
            label = stringResource(R.string.details_action_replace_cover),
            onClick = onReplaceCover,
        )
        DetailsActionRow(
            icon = Icons.Outlined.Description,
            label = stringResource(R.string.details_action_prepare_epub),
            onClick = onPrepareEpub,
        )
        DetailsActionRow(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.details_action_share_export),
            onClick = onShareExport,
        )
        DetailsActionRow(
            icon = Icons.Outlined.Delete,
            label = stringResource(R.string.details_action_delete_book),
            onClick = onDeleteBook,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(24.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.details_about_book).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
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

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(116.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
