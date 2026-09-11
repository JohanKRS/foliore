package com.foliore.android.ui.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.metadata.*
import java.io.File

@Composable
internal fun MetadataFieldSelection(candidate: MetadataLookupCandidate, selected: Set<MetadataField>, onToggle: (MetadataField) -> Unit) {
    val m = candidate.metadata
    val locale = LocalConfiguration.current.locales[0]
    Column {
        MetadataField.entries.filter { it.available(m) || it == MetadataField.COVER }.forEach { field ->
            val value = when (field) {
                MetadataField.TITLE -> m.title
                MetadataField.SUBTITLE -> m.subtitle.orEmpty()
                MetadataField.AUTHORS -> m.authors.joinToString(", ")
                MetadataField.SORT_AUTHOR -> m.sortAuthor.orEmpty()
                MetadataField.SERIES -> m.seriesName.orEmpty()
                MetadataField.SERIES_INDEX -> m.seriesIndex?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
                MetadataField.LANGUAGE -> MetadataLanguages.display(m.language, locale)
                MetadataField.PUBLISHER -> m.publisher.orEmpty()
                MetadataField.PUBLISHED_DATE -> m.publishedDate.orEmpty()
                MetadataField.TAGS -> m.tags.joinToString(", ")
                MetadataField.ISBN -> m.isbn.orEmpty()
                MetadataField.DESCRIPTION -> m.description.orEmpty()
                MetadataField.IDENTIFIERS -> m.identifiers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                MetadataField.COVER -> ""
            }
            val enabled = field != MetadataField.COVER || candidate.coverImagePath != null
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(value = enabled && field in selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(field) })
                    .heightIn(min = 48.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = enabled && field in selected, enabled = enabled, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(field.label()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (field == MetadataField.COVER) {
                        if (candidate.coverImagePath != null) {
                            AsyncImage(model = File(candidate.coverImagePath), contentDescription = null,
                                modifier = Modifier.heightIn(max = 180.dp).fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        } else {
                            Text(stringResource(R.string.details_not_available), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = when (field) { MetadataField.DESCRIPTION -> 7; MetadataField.TAGS -> 4; else -> Int.MAX_VALUE },
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun MetadataField.label(): Int = when (this) {
    MetadataField.TITLE -> R.string.edit_metadata_field_title
    MetadataField.SUBTITLE -> R.string.edit_metadata_field_subtitle
    MetadataField.AUTHORS -> R.string.edit_metadata_field_authors
    MetadataField.SORT_AUTHOR -> R.string.edit_metadata_field_sort_author
    MetadataField.SERIES -> R.string.edit_metadata_field_series
    MetadataField.SERIES_INDEX -> R.string.edit_metadata_field_series_index
    MetadataField.LANGUAGE -> R.string.edit_metadata_field_language
    MetadataField.PUBLISHER -> R.string.edit_metadata_field_publisher
    MetadataField.PUBLISHED_DATE -> R.string.edit_metadata_field_published_date
    MetadataField.TAGS -> R.string.edit_metadata_field_tags
    MetadataField.ISBN -> R.string.edit_metadata_field_isbn
    MetadataField.DESCRIPTION -> R.string.edit_metadata_field_description
    MetadataField.IDENTIFIERS -> R.string.metadata_lookup_identifiers
    MetadataField.COVER -> R.string.metadata_lookup_cover
}
