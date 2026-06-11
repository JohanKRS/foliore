package com.foliore.android.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.foliore.android.R
import com.foliore.android.viewmodel.BookFilter
import com.foliore.android.viewmodel.LibraryUiState
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.SyncStatus
import java.io.File

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onImportClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (BookFilter) -> Unit,
    onBookClick: (String) -> Unit,
) {
    val visibleBooks = remember(state.books, state.activeFilter, state.query) {
        state.visibleBooks()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LibraryHeader(
                isSearchOpen = state.isSearchOpen,
                query = state.query,
                onQueryChange = onQueryChange,
                onSearchClick = onSearchClick,
                onCloseSearch = onCloseSearch,
                onImportClick = onImportClick,
            )
            EditorialDivider(modifier = Modifier.fillMaxWidth())

            if (state.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            FilterRow(
                selectedFilter = state.activeFilter,
                onFilterChange = onFilterChange,
            )

            when {
                state.books.isEmpty() -> {
                    EmptyLibrary(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        onImportClick = onImportClick,
                    )
                }
                visibleBooks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                else -> {
                    BookGrid(
                        books = visibleBooks,
                        onBookClick = onBookClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    isSearchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, top = 24.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                )
            }
            IconButton(onClick = onImportClick) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_import_epubs),
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
        }

        AnimatedVisibility(visible = isSearchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.search_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = onCloseSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear_search),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: BookFilter,
    onFilterChange: (BookFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(text = stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
private fun BookGrid(
    books: List<LibraryBook>,
    onBookClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = books,
            key = { it.book.id },
        ) { item ->
            BookCoverItem(
                item = item,
                onClick = { onBookClick(item.book.id) },
            )
        }
    }
}

@Composable
private fun BookCoverItem(
    item: LibraryBook,
    onClick: () -> Unit,
) {
    val title = item.metadata.title
    val author = item.metadata.authors.firstOrNull().orEmpty()
    val coverPath = item.book.coverThumbPath ?: item.book.coverImagePath

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = 5.dp,
                    shape = RoundedCornerShape(6.dp),
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f),
                )
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        ) {
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = stringResource(R.string.cover_content_description, title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CoverPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = author.ifBlank { stringResource(R.string.book_unknown_author) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusPill(
            status = item.book.status,
            syncStatus = item.book.syncStatus,
        )
    }
}

@Composable
fun StatusPill(
    status: BookStatus,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val label = when {
        syncStatus == SyncStatus.CONFLICT -> stringResource(R.string.status_conflict)
        syncStatus == SyncStatus.SYNC_ERROR -> stringResource(R.string.status_sync_error)
        syncStatus == SyncStatus.SYNCED -> stringResource(R.string.status_synced)
        syncStatus == SyncStatus.PENDING_UPLOAD -> stringResource(R.string.status_pending_upload)
        syncStatus == SyncStatus.PENDING_DOWNLOAD -> stringResource(R.string.status_pending_download)
        else -> stringResource(status.labelRes())
    }
    val tint = when {
        syncStatus == SyncStatus.CONFLICT || syncStatus == SyncStatus.SYNC_ERROR -> MaterialTheme.colorScheme.error
        status == BookStatus.ERROR -> MaterialTheme.colorScheme.error
        syncStatus == SyncStatus.SYNCED -> MaterialTheme.colorScheme.secondary
        status == BookStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.72f)),
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.78f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.dp.toPx())
            val corner = size.minDimension * 0.16f
            drawLine(lineColor, Offset.Zero, Offset(corner, 0f), stroke.width)
            drawLine(lineColor, Offset.Zero, Offset(0f, corner), stroke.width)
            drawLine(lineColor, Offset(size.width, 0f), Offset(size.width - corner, 0f), stroke.width)
            drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, corner), stroke.width)
            drawLine(lineColor, Offset(0f, size.height), Offset(corner, size.height), stroke.width)
            drawLine(lineColor, Offset(0f, size.height), Offset(0f, size.height - corner), stroke.width)
            drawLine(lineColor, Offset(size.width, size.height), Offset(size.width - corner, size.height), stroke.width)
            drawLine(lineColor, Offset(size.width, size.height), Offset(size.width, size.height - corner), stroke.width)
        }
        Text(
            text = stringResource(R.string.cover_placeholder),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.empty_library_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_library_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        AssistChip(
            onClick = onImportClick,
            label = { Text(text = stringResource(R.string.action_import_epubs)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
fun EditorialDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(8.dp)
                .rotate(45f)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)),
        )
    }
}

private fun LibraryUiState.visibleBooks(): List<LibraryBook> {
    val filteredByStatus = books.filter { item ->
        when (activeFilter) {
            BookFilter.ALL -> true
            BookFilter.NEEDS_REVIEW -> item.book.status == BookStatus.NEEDS_REVIEW
            BookFilter.PREPARED -> item.book.status == BookStatus.PREPARED
            BookFilter.SYNCED -> item.book.syncStatus == SyncStatus.SYNCED
            BookFilter.CONFLICT -> item.book.syncStatus == SyncStatus.CONFLICT
            BookFilter.ERROR -> item.book.status == BookStatus.ERROR
        }
    }
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return filteredByStatus

    return filteredByStatus.filter { item ->
        item.metadata.title.contains(trimmedQuery, ignoreCase = true) ||
            item.metadata.authors.any { it.contains(trimmedQuery, ignoreCase = true) }
    }
}

private fun BookFilter.labelRes(): Int {
    return when (this) {
        BookFilter.ALL -> R.string.filter_all
        BookFilter.NEEDS_REVIEW -> R.string.filter_needs_review
        BookFilter.PREPARED -> R.string.filter_prepared
        BookFilter.SYNCED -> R.string.filter_synced
        BookFilter.CONFLICT -> R.string.filter_conflicts
        BookFilter.ERROR -> R.string.filter_error
    }
}

fun BookStatus.labelRes(): Int {
    return when (this) {
        BookStatus.IMPORTED -> R.string.status_imported
        BookStatus.NEEDS_REVIEW -> R.string.status_needs_review
        BookStatus.EDITED -> R.string.status_edited
        BookStatus.PREPARED -> R.string.status_prepared
        BookStatus.OUTDATED_PREPARED -> R.string.status_outdated_prepared
        BookStatus.ERROR -> R.string.status_error
    }
}
