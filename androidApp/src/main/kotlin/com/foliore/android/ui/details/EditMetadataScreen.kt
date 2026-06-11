package com.foliore.android.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook

@Composable
fun EditMetadataScreen(
    book: LibraryBook?,
    onBack: () -> Unit,
    onSave: (BookMetadataDraft) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
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
                onValueChange = {
                    title = it
                    titleTouched = true
                },
                label = stringResource(R.string.edit_metadata_field_title),
                isError = titleError,
                supportingText = if (titleError) stringResource(R.string.edit_metadata_required) else null,
            )
            MetadataTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = stringResource(R.string.edit_metadata_field_subtitle),
            )
            MetadataTextField(
                value = authors,
                onValueChange = { authors = it },
                label = stringResource(R.string.edit_metadata_field_authors),
                supportingText = stringResource(R.string.edit_metadata_field_authors_helper),
            )
            MetadataTextField(
                value = sortAuthor,
                onValueChange = { sortAuthor = it },
                label = stringResource(R.string.edit_metadata_field_sort_author),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataTextField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = stringResource(R.string.edit_metadata_field_series),
                    modifier = Modifier.weight(1f),
                )
                MetadataTextField(
                    value = seriesIndex,
                    onValueChange = { seriesIndex = it },
                    label = stringResource(R.string.edit_metadata_field_series_index),
                    modifier = Modifier.weight(0.72f),
                    keyboardType = KeyboardType.Decimal,
                )
            }
            MetadataTextField(
                value = language,
                onValueChange = { language = it },
                label = stringResource(R.string.edit_metadata_field_language),
            )
            MetadataTextField(
                value = publisher,
                onValueChange = { publisher = it },
                label = stringResource(R.string.edit_metadata_field_publisher),
            )
            MetadataTextField(
                value = publishedDate,
                onValueChange = { publishedDate = it },
                label = stringResource(R.string.edit_metadata_field_published_date),
            )
            MetadataTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.edit_metadata_field_description),
                minLines = 4,
            )
            MetadataTextField(
                value = tags,
                onValueChange = { tags = it },
                label = stringResource(R.string.edit_metadata_field_tags),
                supportingText = stringResource(R.string.edit_metadata_field_tags_helper),
            )
            MetadataTextField(
                value = isbn,
                onValueChange = { isbn = it },
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
                    onClick = {
                        titleTouched = true
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
                    },
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
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text = text) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun String.cleanOptional(): String? = trim().takeIf { it.isNotEmpty() }

private fun String.splitValues(): List<String> {
    return split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
