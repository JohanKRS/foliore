package com.foliore.android.ui.library

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.foliore.android.R
import com.foliore.android.viewmodel.BookFilter
import com.foliore.android.viewmodel.BookGridColumns
import com.foliore.android.viewmodel.BookSortOrder
import com.foliore.android.viewmodel.LibraryUiState
import com.foliore.android.viewmodel.SeriesFilter
import com.foliore.shared.domain.BatchMetadataPatch
import com.foliore.shared.domain.BookMetadata
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
    onSortOrderChange: (BookSortOrder) -> Unit,
    onSeriesFilterChange: (SeriesFilter) -> Unit,
    onGridColumnsChange: (BookGridColumns) -> Unit,
    onBookClick: (String) -> Unit,
    onBatchModeChange: (Boolean) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
    onSelectBooks: (List<String>) -> Unit,
    onBatchUpdate: (BatchMetadataPatch, () -> Unit) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val visibleBooks = remember(
        state.books,
        state.activeFilter,
        state.seriesFilter,
        state.sortOrder,
        state.query,
    ) {
        state.visibleBooks()
    }
    var showBatchDialog by rememberSaveable { mutableStateOf(false) }
    var showControlsDialog by rememberSaveable { mutableStateOf(false) }

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
                hasBooks = state.books.isNotEmpty(),
                isBatchMode = state.isBatchMode,
                onBatchModeChange = onBatchModeChange,
                onOrganizeClick = { showControlsDialog = true },
            )
            EditorialDivider(modifier = Modifier.fillMaxWidth())

            if (state.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            AnimatedVisibility(visible = state.hasActiveLibraryControls()) {
                LibraryControlsSummary(state = state)
            }

            AnimatedVisibility(visible = state.isBatchMode) {
                BatchActionBar(
                    selectedCount = state.selectedBookIds.size,
                    visibleBookCount = visibleBooks.size,
                    onSelectVisible = {
                        onSelectBooks(visibleBooks.map { it.book.id })
                    },
                    onEdit = { showBatchDialog = true },
                    onCancel = {
                        showBatchDialog = false
                        onBatchModeChange(false)
                    },
                )
            }

            when {
                state.books.isEmpty() -> {
                    EmptyLibrary(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        onImportClick = onImportClick,
                        onSettingsClick = onSettingsClick,
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
                        gridColumns = state.gridColumns,
                        isBatchMode = state.isBatchMode,
                        selectedBookIds = state.selectedBookIds,
                        onBookClick = onBookClick,
                        onBookSelectionToggle = onBookSelectionToggle,
                    )
                }
            }
        }
    }

    if (showBatchDialog && state.selectedBookIds.isNotEmpty()) {
        BatchMetadataDialog(
            selectedCount = state.selectedBookIds.size,
            onDismiss = { showBatchDialog = false },
            onApply = { patch ->
                onBatchUpdate(patch) {
                    showBatchDialog = false
                }
            },
        )
    }

    if (showControlsDialog) {
        LibraryControlsDialog(
            selectedFilter = state.activeFilter,
            selectedSortOrder = state.sortOrder,
            selectedSeriesFilter = state.seriesFilter,
            selectedGridColumns = state.gridColumns,
            onFilterChange = onFilterChange,
            onSortOrderChange = onSortOrderChange,
            onSeriesFilterChange = onSeriesFilterChange,
            onGridColumnsChange = onGridColumnsChange,
            onBatchModeChange = {
                showControlsDialog = false
                onBatchModeChange(true)
            },
            onDismiss = { showControlsDialog = false },
        )
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
    hasBooks: Boolean,
    isBatchMode: Boolean,
    onBatchModeChange: (Boolean) -> Unit,
    onOrganizeClick: () -> Unit,
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
            if (hasBooks && isBatchMode) {
                LibraryHeaderIconButton(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel_batch),
                    onClick = { onBatchModeChange(!isBatchMode) },
                )
            }
            if (!isBatchMode) {
                if (hasBooks) {
                    LibraryHeaderIconButton(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_organize_library),
                        onClick = onOrganizeClick,
                    )
                }
                LibraryHeaderIconButton(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                    onClick = onSearchClick,
                )
                LibraryHeaderIconButton(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_import_epubs),
                    onClick = onImportClick,
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
private fun LibraryHeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.82f else 0.34f),
        )
    }
}

@Composable
private fun LibraryControlsSummary(state: LibraryUiState) {
    Text(
        text = state.controlsSummary(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LibraryControlsDialog(
    selectedFilter: BookFilter,
    selectedSortOrder: BookSortOrder,
    selectedSeriesFilter: SeriesFilter,
    selectedGridColumns: BookGridColumns,
    onFilterChange: (BookFilter) -> Unit,
    onSortOrderChange: (BookSortOrder) -> Unit,
    onSeriesFilterChange: (SeriesFilter) -> Unit,
    onGridColumnsChange: (BookGridColumns) -> Unit,
    onBatchModeChange: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 740.dp)
                .heightIn(max = 720.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
            ) {
                Text(
                    text = stringResource(R.string.organization_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogDivider()
                Spacer(modifier = Modifier.height(10.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (maxWidth >= 420.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                StatusControlSection(
                                    selectedFilter = selectedFilter,
                                    onFilterChange = onFilterChange,
                                )
                                SeriesControlSection(
                                    selectedSeriesFilter = selectedSeriesFilter,
                                    onSeriesFilterChange = onSeriesFilterChange,
                                )
                                GridControlSection(
                                    selectedGridColumns = selectedGridColumns,
                                    onGridColumnsChange = onGridColumnsChange,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                SortControlSection(
                                    selectedSortOrder = selectedSortOrder,
                                    onSortOrderChange = onSortOrderChange,
                                )
                                SelectionControlSection(onBatchModeChange = onBatchModeChange)
                            }
                        }
                    } else {
                        Column {
                            StatusControlSection(
                                selectedFilter = selectedFilter,
                                onFilterChange = onFilterChange,
                            )
                            SortControlSection(
                                selectedSortOrder = selectedSortOrder,
                                onSortOrderChange = onSortOrderChange,
                            )
                            SeriesControlSection(
                                selectedSeriesFilter = selectedSeriesFilter,
                                onSeriesFilterChange = onSeriesFilterChange,
                            )
                            GridControlSection(
                                selectedGridColumns = selectedGridColumns,
                                onGridColumnsChange = onGridColumnsChange,
                            )
                            SelectionControlSection(onBatchModeChange = onBatchModeChange)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(6.dp)
                .rotate(45f)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
    }
}

@Composable
private fun StatusControlSection(
    selectedFilter: BookFilter,
    onFilterChange: (BookFilter) -> Unit,
) {
    LibraryControlSectionCard(label = stringResource(R.string.organization_status_label)) {
        LibraryControlOptionGrid(
            columns = 2,
            options = BookFilter.values().map { filter ->
                LibraryControlItem(
                    label = stringResource(filter.labelRes()),
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                )
            },
        )
    }
}

@Composable
private fun SortControlSection(
    selectedSortOrder: BookSortOrder,
    onSortOrderChange: (BookSortOrder) -> Unit,
) {
    LibraryControlSectionCard(label = stringResource(R.string.organization_sort_label)) {
        LibraryControlOptionGrid(
            columns = 1,
            options = BookSortOrder.values().map { sortOrder ->
                LibraryControlItem(
                    label = stringResource(sortOrder.labelRes()),
                    selected = selectedSortOrder == sortOrder,
                    onClick = { onSortOrderChange(sortOrder) },
                )
            },
        )
    }
}

@Composable
private fun SeriesControlSection(
    selectedSeriesFilter: SeriesFilter,
    onSeriesFilterChange: (SeriesFilter) -> Unit,
) {
    LibraryControlSectionCard(label = stringResource(R.string.organization_series_label)) {
        LibraryControlOptionGrid(
            columns = 1,
            options = SeriesFilter.values().map { filter ->
                LibraryControlItem(
                    label = stringResource(filter.labelRes()),
                    selected = selectedSeriesFilter == filter,
                    onClick = { onSeriesFilterChange(filter) },
                )
            },
        )
    }
}

@Composable
private fun GridControlSection(
    selectedGridColumns: BookGridColumns,
    onGridColumnsChange: (BookGridColumns) -> Unit,
) {
    LibraryControlSectionCard(label = stringResource(R.string.organization_grid_label)) {
        LibraryControlOptionGrid(
            columns = 3,
            options = BookGridColumns.values().map { columns ->
                LibraryControlItem(
                    label = stringResource(columns.labelRes()),
                    selected = selectedGridColumns == columns,
                    onClick = { onGridColumnsChange(columns) },
                )
            },
        )
    }
}

@Composable
private fun SelectionControlSection(onBatchModeChange: () -> Unit) {
    LibraryControlSectionCard(label = stringResource(R.string.organization_selection_label)) {
        LibraryControlOptionGrid(
            columns = 1,
            options = listOf(
                LibraryControlItem(
                    label = stringResource(R.string.action_batch_edit),
                    selected = false,
                    onClick = onBatchModeChange,
                ),
            ),
        )
    }
}

@Composable
private fun LibraryControlSectionCard(
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun LibraryControlOptionGrid(
    options: List<LibraryControlItem>,
    columns: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { index ->
                    val item = rowItems.getOrNull(index)
                    if (item != null) {
                        LibraryControlTile(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryControlTile(
    item: LibraryControlItem,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (item.selected) {
            selectedColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (item.selected) {
                selectedColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = if (item.selected) {
                    selectedColor
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = selectedColor,
                )
            }
        }
    }
}

private data class LibraryControlItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    visibleBookCount: Int,
    onSelectVisible: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onEdit,
            enabled = selectedCount > 0,
            label = { Text(text = stringResource(R.string.batch_edit_selected)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                )
            },
        )
        AssistChip(
            onClick = onSelectVisible,
            enabled = visibleBookCount > 0,
            label = { Text(text = stringResource(R.string.batch_select_visible)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                )
            },
        )
        AssistChip(
            onClick = onCancel,
            label = { Text(text = stringResource(R.string.action_cancel_batch)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                )
            },
        )
        Text(
            text = selectedCountText(selectedCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BookGrid(
    books: List<LibraryBook>,
    gridColumns: BookGridColumns,
    isBatchMode: Boolean,
    selectedBookIds: Set<String>,
    onBookClick: (String) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnCount = gridColumns.columnCount(maxWidth)
        val rows = remember(books, columnCount) { books.chunked(columnCount) }

        LazyColumn(
            contentPadding = PaddingValues(
                start = BookGridHorizontalPadding,
                end = BookGridHorizontalPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(BookGridVerticalSpacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = rows,
                key = { row -> row.joinToString(separator = "|") { it.book.id } },
            ) { rowBooks ->
                BookGridRow(
                    books = rowBooks,
                    columnCount = columnCount,
                    isBatchMode = isBatchMode,
                    selectedBookIds = selectedBookIds,
                    onBookClick = onBookClick,
                    onBookSelectionToggle = onBookSelectionToggle,
                )
            }
        }
    }
}

@Composable
private fun BookGridRow(
    books: List<LibraryBook>,
    columnCount: Int,
    isBatchMode: Boolean,
    selectedBookIds: Set<String>,
    onBookClick: (String) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
) {
    val coverKeys = remember(books) { books.map { it.book.id to it.coverPath() } }
    val coverAspectRatios = remember(coverKeys) {
        coverKeys.associate { (bookId, coverPath) ->
            bookId to coverPath.coverAspectRatio()
        }
    }
    val rowCoverAspectRatio = coverAspectRatios.values.minOrNull() ?: DefaultCoverAspectRatio

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BookGridHorizontalSpacing),
    ) {
        repeat(columnCount) { index ->
            val item = books.getOrNull(index)
            Box(modifier = Modifier.weight(1f)) {
                if (item != null) {
                    BookCoverItem(
                        item = item,
                        coverAspectRatio = coverAspectRatios[item.book.id] ?: DefaultCoverAspectRatio,
                        rowCoverAspectRatio = rowCoverAspectRatio,
                        isBatchMode = isBatchMode,
                        isSelected = item.book.id in selectedBookIds,
                        onClick = {
                            if (isBatchMode) {
                                onBookSelectionToggle(item.book.id)
                            } else {
                                onBookClick(item.book.id)
                            }
                        },
                        onLongClick = { onBookSelectionToggle(item.book.id) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookCoverItem(
    item: LibraryBook,
    coverAspectRatio: Float,
    rowCoverAspectRatio: Float,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = item.metadata.title
    val author = item.metadata.authors.firstOrNull().orEmpty()
    val seriesLabel = item.metadata.seriesLabel()
    val coverPath = item.coverPath()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(rowCoverAspectRatio),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            ) {
                if (coverPath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(coverPath)),
                        contentDescription = stringResource(R.string.cover_content_description, title),
                        alignment = Alignment.BottomCenter,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CoverPlaceholder(modifier = Modifier.fillMaxSize())
                }

                if (isBatchMode) {
                    SelectionBadge(
                        selected = isSelected,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = author.ifBlank { stringResource(R.string.book_unknown_author) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (seriesLabel != null) {
            Text(
                text = seriesLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        StatusPill(
            status = item.book.status,
            syncStatus = item.book.syncStatus,
        )
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.size(28.dp),
        shape = RoundedCornerShape(14.dp),
        color = badgeColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
private fun BatchMetadataDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApply: (BatchMetadataPatch) -> Unit,
) {
    var authors by rememberSaveable { mutableStateOf("") }
    var sortAuthor by rememberSaveable { mutableStateOf("") }
    var seriesName by rememberSaveable { mutableStateOf("") }
    var seriesIndexStart by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var publisher by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }

    val parsedSeriesIndexStart = seriesIndexStart.cleanOptional()?.toDoubleOrNull()
    val seriesIndexHasError = seriesIndexStart.cleanOptional() != null && parsedSeriesIndexStart == null
    val hasChanges = listOf(authors, sortAuthor, seriesName, seriesIndexStart, language, publisher, tags)
        .any { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.batch_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = selectedCountText(selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.batch_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BatchMetadataField(
                    value = authors,
                    onValueChange = { authors = it },
                    label = stringResource(R.string.edit_metadata_field_authors),
                    supportingText = stringResource(R.string.edit_metadata_field_authors_helper),
                )
                BatchMetadataField(
                    value = sortAuthor,
                    onValueChange = { sortAuthor = it },
                    label = stringResource(R.string.edit_metadata_field_sort_author),
                )
                BatchMetadataField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = stringResource(R.string.edit_metadata_field_series),
                )
                BatchMetadataField(
                    value = seriesIndexStart,
                    onValueChange = { seriesIndexStart = it },
                    label = stringResource(R.string.batch_metadata_field_series_start),
                    supportingText = if (seriesIndexHasError) {
                        stringResource(R.string.batch_metadata_invalid_number)
                    } else {
                        stringResource(R.string.batch_metadata_field_series_start_helper)
                    },
                    isError = seriesIndexHasError,
                    keyboardType = KeyboardType.Decimal,
                )
                BatchMetadataField(
                    value = language,
                    onValueChange = { language = it },
                    label = stringResource(R.string.edit_metadata_field_language),
                )
                BatchMetadataField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = stringResource(R.string.edit_metadata_field_publisher),
                )
                BatchMetadataField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = stringResource(R.string.edit_metadata_field_tags),
                    supportingText = stringResource(R.string.edit_metadata_field_tags_helper),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        BatchMetadataPatch(
                            authors = authors.splitValues().takeIf { it.isNotEmpty() },
                            sortAuthor = sortAuthor.cleanOptional(),
                            language = language.cleanOptional(),
                            publisher = publisher.cleanOptional(),
                            tags = tags.splitValues().takeIf { it.isNotEmpty() },
                            seriesName = seriesName.cleanOptional(),
                            seriesIndexStart = parsedSeriesIndexStart,
                        ),
                    )
                },
                enabled = hasChanges && !seriesIndexHasError,
            ) {
                Text(text = stringResource(R.string.batch_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.edit_metadata_cancel))
            }
        },
    )
}

@Composable
private fun BatchMetadataField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text = text) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
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
        shape = RoundedCornerShape(3.dp),
        color = Color.Transparent,
        border = BorderStroke(0.75.dp, tint.copy(alpha = 0.48f)),
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.sp,
            ),
            color = tint.copy(alpha = 0.9f),
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
    onSettingsClick: () -> Unit,
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
        Spacer(modifier = Modifier.height(10.dp))
        AssistChip(
            onClick = onSettingsClick,
            label = { Text(text = stringResource(R.string.empty_library_sync_setup)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Sync,
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

private fun String.splitValues(): List<String> {
    return split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
private fun selectedCountText(count: Int): String {
    return stringResource(
        id = if (count == 1) R.string.batch_selected_count_one else R.string.batch_selected_count_many,
        count,
    )
}

private val BookGridHorizontalPadding = 28.dp
private val BookGridHorizontalSpacing = 28.dp
private val BookGridVerticalSpacing = 28.dp
private val BookGridMinCellWidth = 150.dp
private const val DefaultCoverAspectRatio = 2f / 3f

private fun LibraryUiState.visibleBooks(): List<LibraryBook> {
    val trimmedQuery = query.trim()

    return books
        .filter { item -> item.matchesStatusFilter(activeFilter) }
        .filter { item -> item.matchesSeriesFilter(seriesFilter) }
        .filter { item ->
            trimmedQuery.isBlank() ||
                item.metadata.title.contains(trimmedQuery, ignoreCase = true) ||
                item.metadata.authors.any { it.contains(trimmedQuery, ignoreCase = true) } ||
                item.metadata.seriesName?.contains(trimmedQuery, ignoreCase = true) == true
        }
        .sortedBy(sortOrder)
}

private fun LibraryUiState.hasActiveLibraryControls(): Boolean {
    return activeFilter != BookFilter.ALL ||
        sortOrder != BookSortOrder.IMPORTED_NEWEST ||
        seriesFilter != SeriesFilter.ALL ||
        gridColumns != BookGridColumns.AUTO
}

@Composable
private fun LibraryUiState.controlsSummary(): String {
    val parts = mutableListOf<String>()
    if (activeFilter != BookFilter.ALL) {
        parts += stringResource(activeFilter.labelRes())
    }
    if (sortOrder != BookSortOrder.IMPORTED_NEWEST) {
        parts += stringResource(sortOrder.labelRes())
    }
    if (seriesFilter != SeriesFilter.ALL) {
        parts += stringResource(seriesFilter.labelRes())
    }
    if (gridColumns != BookGridColumns.AUTO) {
        parts += stringResource(gridColumns.labelRes())
    }
    return parts.joinToString(separator = " · ")
}

private fun LibraryBook.matchesStatusFilter(filter: BookFilter): Boolean {
    return when (filter) {
        BookFilter.ALL -> true
        BookFilter.NEEDS_REVIEW -> book.status == BookStatus.NEEDS_REVIEW
        BookFilter.PREPARED -> book.status == BookStatus.PREPARED
        BookFilter.SYNCED -> book.syncStatus == SyncStatus.SYNCED
        BookFilter.CONFLICT -> book.syncStatus == SyncStatus.CONFLICT
        BookFilter.ERROR -> book.status == BookStatus.ERROR
    }
}

private fun LibraryBook.matchesSeriesFilter(filter: SeriesFilter): Boolean {
    val hasSeries = metadata.seriesName?.isNotBlank() == true
    return when (filter) {
        SeriesFilter.ALL -> true
        SeriesFilter.WITH_SERIES -> hasSeries
        SeriesFilter.WITHOUT_SERIES -> !hasSeries
    }
}

private fun List<LibraryBook>.sortedBy(sortOrder: BookSortOrder): List<LibraryBook> {
    return when (sortOrder) {
        BookSortOrder.IMPORTED_NEWEST -> sortedWith(
            compareByDescending<LibraryBook> { it.book.importedAt }
                .thenBy { it.titleSortKey() },
        )
        BookSortOrder.TITLE_ASC -> sortedBy { it.titleSortKey() }
        BookSortOrder.TITLE_DESC -> sortedWith(compareByDescending<LibraryBook> { it.titleSortKey() })
        BookSortOrder.AUTHOR_ASC -> sortedWith(
            compareBy<LibraryBook> { it.authorSortKey() == null }
                .thenBy { it.authorSortKey().orEmpty() }
                .thenBy { it.titleSortKey() },
        )
        BookSortOrder.AUTHOR_DESC -> sortedWith(
            compareBy<LibraryBook> { it.authorSortKey() == null }
                .thenByDescending { it.authorSortKey().orEmpty() }
                .thenBy { it.titleSortKey() },
        )
        BookSortOrder.YEAR_DESC -> sortedWith(
            compareBy<LibraryBook> { it.publishedYear() == null }
                .thenByDescending { it.publishedYear() ?: Int.MIN_VALUE }
                .thenBy { it.titleSortKey() },
        )
        BookSortOrder.YEAR_ASC -> sortedWith(
            compareBy<LibraryBook> { it.publishedYear() == null }
                .thenBy { it.publishedYear() ?: Int.MAX_VALUE }
                .thenBy { it.titleSortKey() },
        )
        BookSortOrder.SERIES -> sortedWith(
            compareBy<LibraryBook> { it.metadata.seriesName?.isBlank() != false }
                .thenBy { it.metadata.seriesName.sortKey() }
                .thenBy { it.metadata.seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.titleSortKey() },
        )
    }
}

private fun LibraryBook.titleSortKey(): String = metadata.title.sortKey()

private fun LibraryBook.coverPath(): String? = book.coverThumbPath ?: book.coverImagePath

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

private fun LibraryBook.authorSortKey(): String? {
    return metadata.sortAuthor.cleanOptional()
        ?: metadata.authors.firstOrNull()?.cleanOptional()
}

private fun LibraryBook.publishedYear(): Int? {
    return metadata.publishedDate
        ?.let { yearRegex.find(it)?.value }
        ?.toIntOrNull()
}

private fun BookMetadata.seriesLabel(): String? {
    val name = seriesName.cleanOptional() ?: return null
    val index = seriesIndex?.displayValue()
    return if (index == null) name else "$name #$index"
}

private fun Double.displayValue(): String {
    return if (this % 1.0 == 0.0) toLong().toString() else toString()
}

private fun String?.cleanOptional(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.sortKey(): String = cleanOptional()?.lowercase().orEmpty()

private val yearRegex = Regex("\\d{4}")

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

private fun BookSortOrder.labelRes(): Int {
    return when (this) {
        BookSortOrder.IMPORTED_NEWEST -> R.string.sort_imported_newest
        BookSortOrder.TITLE_ASC -> R.string.sort_title_asc
        BookSortOrder.TITLE_DESC -> R.string.sort_title_desc
        BookSortOrder.AUTHOR_ASC -> R.string.sort_author_asc
        BookSortOrder.AUTHOR_DESC -> R.string.sort_author_desc
        BookSortOrder.YEAR_DESC -> R.string.sort_year_desc
        BookSortOrder.YEAR_ASC -> R.string.sort_year_asc
        BookSortOrder.SERIES -> R.string.sort_series
    }
}

private fun SeriesFilter.labelRes(): Int {
    return when (this) {
        SeriesFilter.ALL -> R.string.series_filter_all
        SeriesFilter.WITH_SERIES -> R.string.series_filter_with_series
        SeriesFilter.WITHOUT_SERIES -> R.string.series_filter_without_series
    }
}

private fun BookGridColumns.labelRes(): Int {
    return when (this) {
        BookGridColumns.AUTO -> R.string.grid_columns_auto
        BookGridColumns.TWO -> R.string.grid_columns_two
        BookGridColumns.THREE -> R.string.grid_columns_three
        BookGridColumns.FOUR -> R.string.grid_columns_four
        BookGridColumns.FIVE -> R.string.grid_columns_five
        BookGridColumns.SIX -> R.string.grid_columns_six
    }
}

private fun BookGridColumns.columnCount(availableWidth: Dp): Int {
    fixedCount?.let { return it }

    val contentWidth = (availableWidth - (BookGridHorizontalPadding * 2)).value
        .coerceAtLeast(BookGridMinCellWidth.value)
    val columnWidthWithGap = BookGridMinCellWidth.value + BookGridHorizontalSpacing.value
    return ((contentWidth + BookGridHorizontalSpacing.value) / columnWidthWithGap)
        .toInt()
        .coerceAtLeast(1)
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
