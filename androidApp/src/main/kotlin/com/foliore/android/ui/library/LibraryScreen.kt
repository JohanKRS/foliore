package com.foliore.android.ui.library

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.Disposable
import coil.request.ImageRequest
import com.foliore.android.R
import com.foliore.android.ui.FolioreTopLevelHeader
import com.foliore.android.ui.TopChromeDividerHalfHeight
import com.foliore.android.ui.TopChromeHeaderHeight
import com.foliore.android.ui.theme.CormorantGaramondFamily
import com.foliore.android.ui.tuning.LocalVisualTuning
import com.foliore.android.viewmodel.BookFilter
import com.foliore.android.viewmodel.BookGridColumns
import com.foliore.android.viewmodel.BookSortOrder
import com.foliore.android.viewmodel.LibraryDisplayOptions
import com.foliore.android.viewmodel.LibraryUiState
import com.foliore.android.viewmodel.SeriesFilter
import com.foliore.shared.domain.BatchMetadataPatch
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.SyncStatus
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onShowBookTitleChange: (Boolean) -> Unit,
    onShowBookAuthorChange: (Boolean) -> Unit,
    onShowBookSeriesChange: (Boolean) -> Unit,
    onBookClick: (String) -> Unit,
    onBatchModeChange: (Boolean) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
    onSelectBooks: (List<String>) -> Unit,
    onBatchUpdate: (BatchMetadataPatch, () -> Unit) -> Unit,
    onBatchDelete: () -> Unit,
    onSettingsClick: () -> Unit,
    gridScrollState: BookGridScrollState,
    chromeHazeState: HazeState? = null,
    topChromeInset: Dp = 0.dp,
    bottomChromeInset: Dp = 0.dp,
    showTopChrome: Boolean = true,
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
    val localHazeState = rememberHazeState()
    val hazeState = chromeHazeState ?: localHazeState
    val density = LocalDensity.current
    var topChromeHeightPx by remember { mutableStateOf(0) }
    val resolvedTopChromeInset = if (showTopChrome) {
        with(density) { topChromeHeightPx.toDp() }
    } else {
        topChromeInset
    }
    val tuning = LocalVisualTuning.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomChromeInset),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSource(state = hazeState),
                            books = visibleBooks,
                            gridColumns = state.gridColumns,
                            displayOptions = state.displayOptions,
                            topContentPadding = resolvedTopChromeInset + tuning.libraryGridTopExtra.dp,
                            bottomContentPadding = bottomChromeInset + tuning.libraryGridBottomExtra.dp,
                            scrollState = gridScrollState,
                            restoreReady = if (showTopChrome) {
                                topChromeHeightPx > 0
                            } else {
                                resolvedTopChromeInset > 0.dp
                            },
                            isBatchMode = state.isBatchMode,
                            selectedBookIds = state.selectedBookIds,
                            onBookClick = onBookClick,
                            onBookSelectionToggle = onBookSelectionToggle,
                        )
                    }
                }
            }

            if (showTopChrome) {
                LibraryTopChromeOverlay(
                    state = state,
                    hazeState = hazeState,
                    onImportClick = onImportClick,
                    onSearchClick = onSearchClick,
                    onCloseSearch = onCloseSearch,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onSortOrderChange = onSortOrderChange,
                    onSeriesFilterChange = onSeriesFilterChange,
                    onGridColumnsChange = onGridColumnsChange,
                    onShowBookTitleChange = onShowBookTitleChange,
                    onShowBookAuthorChange = onShowBookAuthorChange,
                    onShowBookSeriesChange = onShowBookSeriesChange,
                    onBatchModeChange = onBatchModeChange,
                    onSelectBooks = onSelectBooks,
                    onBatchUpdate = onBatchUpdate,
                    onBatchDelete = onBatchDelete,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topChromeHeightPx = it.height },
                )
            }
        }
    }
}

@Composable
fun LibraryTopChromeOverlay(
    state: LibraryUiState,
    hazeState: HazeState,
    onImportClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (BookFilter) -> Unit,
    onSortOrderChange: (BookSortOrder) -> Unit,
    onSeriesFilterChange: (SeriesFilter) -> Unit,
    onGridColumnsChange: (BookGridColumns) -> Unit,
    onShowBookTitleChange: (Boolean) -> Unit,
    onShowBookAuthorChange: (Boolean) -> Unit,
    onShowBookSeriesChange: (Boolean) -> Unit,
    onBatchModeChange: (Boolean) -> Unit,
    onSelectBooks: (List<String>) -> Unit,
    onBatchUpdate: (BatchMetadataPatch, () -> Unit) -> Unit,
    onBatchDelete: () -> Unit,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
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
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showControlsDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler(
        enabled = state.isBatchMode && !showBatchDialog && !showBatchDeleteDialog && !showControlsDialog,
    ) {
        onBatchModeChange(false)
    }

    val content: @Composable ColumnScope.() -> Unit = {
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

        AnimatedVisibility(visible = state.isBatchMode) {
            BatchActionBar(
                selectedCount = state.selectedBookIds.size,
                visibleBookCount = visibleBooks.size,
                onSelectVisible = {
                    onSelectBooks(visibleBooks.map { it.book.id })
                },
                onEdit = { showBatchDialog = true },
                onDelete = { showBatchDeleteDialog = true },
                onCancel = {
                    showBatchDialog = false
                    showBatchDeleteDialog = false
                    onBatchModeChange(false)
                },
            )
        }
    }
    if (drawBackground) {
        LibraryTopChrome(
            modifier = modifier,
            hazeState = hazeState,
            content = content,
        )
    } else {
        LibraryTopChromeFrame(
            modifier = modifier,
            content = content,
        )
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

    if (showBatchDeleteDialog && state.selectedBookIds.isNotEmpty()) {
        BatchDeleteDialog(
            selectedCount = state.selectedBookIds.size,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                showBatchDeleteDialog = false
                onBatchDelete()
            },
        )
    }

    if (showControlsDialog) {
        LibraryControlsDialog(
            selectedFilter = state.activeFilter,
            selectedSortOrder = state.sortOrder,
            selectedSeriesFilter = state.seriesFilter,
            selectedGridColumns = state.gridColumns,
            selectedDisplayOptions = state.displayOptions,
            onFilterChange = onFilterChange,
            onSortOrderChange = onSortOrderChange,
            onSeriesFilterChange = onSeriesFilterChange,
            onGridColumnsChange = onGridColumnsChange,
            onShowBookTitleChange = onShowBookTitleChange,
            onShowBookAuthorChange = onShowBookAuthorChange,
            onShowBookSeriesChange = onShowBookSeriesChange,
            onDismiss = { showControlsDialog = false },
        )
    }
}

@Composable
private fun LibraryTopChrome(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chromeTint = MaterialTheme.colorScheme.background
    Box(modifier = modifier) {
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
        LibraryTopChromeFrame(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun LibraryTopChromeFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tuning = LocalVisualTuning.current
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
        EditorialDivider(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = tuning.topChromeDividerHalfHeight.dp)
                .fillMaxWidth(),
            horizontalPadding = 0.dp,
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
    val tuning = LocalVisualTuning.current
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        FolioreTopLevelHeader(
            title = stringResource(R.string.library_title),
        ) {
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
                    .padding(start = 28.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
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
private fun LibraryControlsDialog(
    selectedFilter: BookFilter,
    selectedSortOrder: BookSortOrder,
    selectedSeriesFilter: SeriesFilter,
    selectedGridColumns: BookGridColumns,
    selectedDisplayOptions: LibraryDisplayOptions,
    onFilterChange: (BookFilter) -> Unit,
    onSortOrderChange: (BookSortOrder) -> Unit,
    onSeriesFilterChange: (SeriesFilter) -> Unit,
    onGridColumnsChange: (BookGridColumns) -> Unit,
    onShowBookTitleChange: (Boolean) -> Unit,
    onShowBookAuthorChange: (Boolean) -> Unit,
    onShowBookSeriesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(
        title = stringResource(R.string.organization_dialog_title),
        onDismiss = onDismiss,
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
                    DisplayControlSection(
                        selectedDisplayOptions = selectedDisplayOptions,
                        onShowBookTitleChange = onShowBookTitleChange,
                        onShowBookAuthorChange = onShowBookAuthorChange,
                        onShowBookSeriesChange = onShowBookSeriesChange,
                    )
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
                DisplayControlSection(
                    selectedDisplayOptions = selectedDisplayOptions,
                    onShowBookTitleChange = onShowBookTitleChange,
                    onShowBookAuthorChange = onShowBookAuthorChange,
                    onShowBookSeriesChange = onShowBookSeriesChange,
                )
            }
        }
    }
}

@Composable
private fun StatusControlSection(
    selectedFilter: BookFilter,
    onFilterChange: (BookFilter) -> Unit,
) {
    FolioreDialogSectionCard(label = stringResource(R.string.organization_status_label)) {
        FolioreDialogOptionGrid(
            columns = 2,
            options = BookFilter.values().map { filter ->
                FolioreDialogOption(
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
    FolioreDialogSectionCard(label = stringResource(R.string.organization_sort_label)) {
        FolioreDialogOptionGrid(
            columns = 1,
            options = BookSortOrder.values().map { sortOrder ->
                FolioreDialogOption(
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
    FolioreDialogSectionCard(label = stringResource(R.string.organization_series_label)) {
        FolioreDialogOptionGrid(
            columns = 1,
            options = SeriesFilter.values().map { filter ->
                FolioreDialogOption(
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
    FolioreDialogSectionCard(label = stringResource(R.string.organization_grid_label)) {
        FolioreDialogOptionGrid(
            columns = 3,
            options = BookGridColumns.values().map { columns ->
                FolioreDialogOption(
                    label = stringResource(columns.labelRes()),
                    selected = selectedGridColumns == columns,
                    onClick = { onGridColumnsChange(columns) },
                )
            },
        )
    }
}

@Composable
private fun DisplayControlSection(
    selectedDisplayOptions: LibraryDisplayOptions,
    onShowBookTitleChange: (Boolean) -> Unit,
    onShowBookAuthorChange: (Boolean) -> Unit,
    onShowBookSeriesChange: (Boolean) -> Unit,
) {
    FolioreDialogSectionCard(label = stringResource(R.string.organization_display_label)) {
        FolioreDialogOptionGrid(
            columns = 3,
            options = listOf(
                FolioreDialogOption(
                    label = stringResource(R.string.library_display_title),
                    selected = selectedDisplayOptions.showTitle,
                    onClick = { onShowBookTitleChange(!selectedDisplayOptions.showTitle) },
                ),
                FolioreDialogOption(
                    label = stringResource(R.string.library_display_author),
                    selected = selectedDisplayOptions.showAuthor,
                    onClick = { onShowBookAuthorChange(!selectedDisplayOptions.showAuthor) },
                ),
                FolioreDialogOption(
                    label = stringResource(R.string.library_display_series),
                    selected = selectedDisplayOptions.showSeries,
                    onClick = { onShowBookSeriesChange(!selectedDisplayOptions.showSeries) },
                ),
            ),
        )
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    visibleBookCount: Int,
    onSelectVisible: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            onClick = onDelete,
            enabled = selectedCount > 0,
            label = { Text(text = stringResource(R.string.batch_delete_selected)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
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
    modifier: Modifier = Modifier,
    books: List<LibraryBook>,
    gridColumns: BookGridColumns,
    displayOptions: LibraryDisplayOptions,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 32.dp,
    scrollState: BookGridScrollState,
    restoreReady: Boolean = true,
    isBatchMode: Boolean,
    selectedBookIds: Set<String>,
    onBookClick: (String) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
) {
    val context = LocalContext.current
    val composeView = LocalView.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    val exposeBookSemantics = accessibilityManager?.isTouchExplorationEnabled == true
    val suppressComposeAccessibility = accessibilityManager?.isEnabled == true && !exposeBookSemantics
    val semanticsModifier = if (exposeBookSemantics) {
        Modifier
    } else {
        Modifier.clearAndSetSemantics {}
    }
    val currentOnBookClick by rememberUpdatedState(onBookClick)
    val currentOnBookSelectionToggle by rememberUpdatedState(onBookSelectionToggle)
    val colorScheme = MaterialTheme.colorScheme
    val tuning = LocalVisualTuning.current
    val gridHorizontalPadding = tuning.libraryGridHorizontalPadding.dp
    val gridHorizontalSpacing = tuning.libraryGridHorizontalSpacing.dp
    val gridVerticalSpacing = tuning.libraryGridVerticalSpacing.dp
    val gridStyle = BookGridNativeStyle(
        titleColor = colorScheme.onBackground.toArgb(),
        authorColor = colorScheme.onSurfaceVariant.toArgb(),
        seriesColor = colorScheme.primary.toArgb(),
        placeholderBackgroundColor = colorScheme.surfaceVariant.toArgb(),
        placeholderTextColor = colorScheme.onSurfaceVariant.toArgb(),
        selectedColor = colorScheme.primary.toArgb(),
        selectedContentColor = colorScheme.onPrimary.toArgb(),
        badgeSurfaceColor = colorScheme.surface.toArgb(),
        badgeOutlineColor = colorScheme.outline.toArgb(),
        statusPrimaryColor = colorScheme.primary.toArgb(),
        statusSecondaryColor = colorScheme.secondary.toArgb(),
        statusErrorColor = colorScheme.error.toArgb(),
    )

    DisposableEffect(composeView, suppressComposeAccessibility) {
        val previousImportance = composeView.importantForAccessibility
        if (suppressComposeAccessibility) {
            composeView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        onDispose {
            composeView.importantForAccessibility = previousImportance
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val columnCount = gridColumns.columnCount(
            availableWidth = maxWidth,
            horizontalPadding = gridHorizontalPadding,
            horizontalSpacing = gridHorizontalSpacing,
        )
        val itemLayout = remember(columnCount) {
            BookGridItemLayout.forColumnCount(columnCount)
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .then(semanticsModifier),
            factory = { viewContext ->
                val layoutManager = GridLayoutManager(viewContext, columnCount)
                val adapter = BookGridRecyclerAdapter()
                val decoration = BookGridSpacingDecoration(
                    spanCount = columnCount,
                    horizontalSpacingPx = viewContext.dpToPx(gridHorizontalSpacing),
                    verticalSpacingPx = viewContext.dpToPx(gridVerticalSpacing),
                )

                RecyclerView(viewContext).apply {
                    tag = BookGridRecyclerInternals(
                        adapter = adapter,
                        layoutManager = layoutManager,
                        decoration = decoration,
                        scrollState = scrollState,
                    )
                    this.layoutManager = layoutManager
                    this.adapter = adapter
                    itemAnimator = null
                    setHasFixedSize(true)
                    clipChildren = false
                    clipToPadding = false
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    setPadding(
                        viewContext.dpToPx(gridHorizontalPadding),
                        viewContext.dpToPx(topContentPadding),
                        viewContext.dpToPx(gridHorizontalPadding),
                        viewContext.dpToPx(bottomContentPadding),
                    )
                    addItemDecoration(decoration)
                    addOnScrollListener(
                        object : RecyclerView.OnScrollListener() {
                            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                val internals = recyclerView.tag as? BookGridRecyclerInternals ?: return
                                if (!internals.isRestoringScroll) {
                                    recyclerView.captureScrollState(internals.scrollState)
                                }
                            }

                            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                    val internals = recyclerView.tag as? BookGridRecyclerInternals ?: return
                                    if (!internals.isRestoringScroll) {
                                        recyclerView.captureScrollState(internals.scrollState)
                                    }
                                }
                            }
                        },
                    )
                }
            },
            update = { recyclerView ->
                val internals = recyclerView.tag as BookGridRecyclerInternals
                internals.scrollState = scrollState
                internals.layoutManager.spanCount = columnCount
                internals.decoration.update(
                    spanCount = columnCount,
                    horizontalSpacingPx = recyclerView.context.dpToPx(gridHorizontalSpacing),
                    verticalSpacingPx = recyclerView.context.dpToPx(gridVerticalSpacing),
                )
                recyclerView.setPadding(
                    recyclerView.context.dpToPx(gridHorizontalPadding),
                    recyclerView.context.dpToPx(topContentPadding),
                    recyclerView.context.dpToPx(gridHorizontalPadding),
                    recyclerView.context.dpToPx(bottomContentPadding),
                )
                recyclerView.invalidateItemDecorations()
                internals.adapter.onBookClick = { bookId ->
                    recyclerView.captureScrollState(internals.scrollState)
                    if (isBatchMode) {
                        currentOnBookSelectionToggle(bookId)
                    } else {
                        currentOnBookClick(bookId)
                    }
                }
                internals.adapter.onBookLongClick = { bookId ->
                    currentOnBookSelectionToggle(bookId)
                }
                internals.adapter.submit(
                    books = books,
                    selectedBookIds = selectedBookIds,
                    isBatchMode = isBatchMode,
                    exposeBookSemantics = exposeBookSemantics,
                    style = gridStyle,
                    itemLayout = itemLayout,
                    displayOptions = displayOptions,
                )
                if (restoreReady && !internals.hasRestoredScroll && books.isNotEmpty()) {
                    internals.hasRestoredScroll = true
                    val restoredPosition = internals.scrollState.position.coerceIn(0, books.lastIndex)
                    val restoredOffset = internals.scrollState.offset
                    internals.isRestoringScroll = restoredPosition != 0 || restoredOffset != 0
                    recyclerView.post {
                        internals.layoutManager.scrollToPositionWithOffset(
                            restoredPosition,
                            restoredOffset,
                        )
                        recyclerView.post {
                            internals.isRestoringScroll = false
                            recyclerView.captureScrollState(internals.scrollState)
                        }
                    }
                }
            },
        )
    }
}

private class BookGridRecyclerInternals(
    val adapter: BookGridRecyclerAdapter,
    val layoutManager: GridLayoutManager,
    val decoration: BookGridSpacingDecoration,
    var scrollState: BookGridScrollState,
    var hasRestoredScroll: Boolean = false,
    var isRestoringScroll: Boolean = scrollState.position != 0 || scrollState.offset != 0,
)

class BookGridScrollState(
    var position: Int = 0,
    var offset: Int = 0,
) {
    companion object {
        val Saver = listSaver<BookGridScrollState, Int>(
            save = { listOf(it.position, it.offset) },
            restore = { restored ->
                BookGridScrollState(
                    position = restored.getOrNull(0) ?: 0,
                    offset = restored.getOrNull(1) ?: 0,
                )
            },
        )
    }
}

private fun RecyclerView.captureScrollState(scrollState: BookGridScrollState) {
    val anchorTop = paddingTop
    var child: View? = null
    var childPosition = RecyclerView.NO_POSITION
    var bestDistance = Int.MAX_VALUE

    for (index in 0 until childCount) {
        val candidate = getChildAt(index) ?: continue
        val candidatePosition = getChildAdapterPosition(candidate)
        if (candidatePosition == RecyclerView.NO_POSITION) continue
        val distance = candidate.top - anchorTop
        if (distance >= 0 && distance < bestDistance) {
            child = candidate
            childPosition = candidatePosition
            bestDistance = distance
        }
    }

    if (child == null) {
        for (index in 0 until childCount) {
            val candidate = getChildAt(index) ?: continue
            val candidatePosition = getChildAdapterPosition(candidate)
            if (candidatePosition == RecyclerView.NO_POSITION) continue
            val distance = abs(candidate.top - anchorTop)
            if (distance < bestDistance) {
                child = candidate
                childPosition = candidatePosition
                bestDistance = distance
            }
        }
    }

    val anchorChild = child ?: return
    if (childPosition == RecyclerView.NO_POSITION) return
    scrollState.position = childPosition
    scrollState.offset = anchorChild.top - anchorTop
}

private data class BookGridNativeStyle(
    val titleColor: Int,
    val authorColor: Int,
    val seriesColor: Int,
    val placeholderBackgroundColor: Int,
    val placeholderTextColor: Int,
    val selectedColor: Int,
    val selectedContentColor: Int,
    val badgeSurfaceColor: Int,
    val badgeOutlineColor: Int,
    val statusPrimaryColor: Int,
    val statusSecondaryColor: Int,
    val statusErrorColor: Int,
) {
    fun statusColor(status: BookStatus, syncStatus: SyncStatus): Int {
        return when {
            syncStatus == SyncStatus.CONFLICT || syncStatus == SyncStatus.SYNC_ERROR -> statusErrorColor
            status == BookStatus.ERROR -> statusErrorColor
            status == BookStatus.NEEDS_REVIEW -> statusErrorColor
            syncStatus == SyncStatus.SYNCED -> statusSecondaryColor
            else -> statusPrimaryColor
        }
    }
}

private class BookGridRecyclerAdapter : RecyclerView.Adapter<BookGridViewHolder>() {
    var onBookClick: (String) -> Unit = {}
    var onBookLongClick: (String) -> Unit = {}

    private var books: List<LibraryBook> = emptyList()
    private var selectedBookIds: Set<String> = emptySet()
    private var isBatchMode: Boolean = false
    private var exposeBookSemantics: Boolean = false
    private var style: BookGridNativeStyle? = null
    private var itemLayout: BookGridItemLayout? = null
    private var displayOptions: LibraryDisplayOptions = LibraryDisplayOptions()
    private var coverSlotAspectRatios: FloatArray = FloatArray(0)

    init {
        setHasStableIds(true)
    }

    fun submit(
        books: List<LibraryBook>,
        selectedBookIds: Set<String>,
        isBatchMode: Boolean,
        exposeBookSemantics: Boolean,
        style: BookGridNativeStyle,
        itemLayout: BookGridItemLayout,
        displayOptions: LibraryDisplayOptions,
    ) {
        this.books = books
        this.selectedBookIds = selectedBookIds
        this.isBatchMode = isBatchMode
        this.exposeBookSemantics = exposeBookSemantics
        this.style = style
        this.itemLayout = itemLayout
        this.displayOptions = displayOptions
        this.coverSlotAspectRatios = books.coverSlotAspectRatios(itemLayout.columnCount)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return books[position].book.id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookGridViewHolder {
        return BookGridViewHolder(BookGridItemView(parent.context))
    }

    override fun onBindViewHolder(holder: BookGridViewHolder, position: Int) {
        val book = books[position]
        holder.item.bind(
            item = book,
            isSelected = book.book.id in selectedBookIds,
            isBatchMode = isBatchMode,
            exposeBookSemantics = exposeBookSemantics,
            style = checkNotNull(style),
            itemLayout = checkNotNull(itemLayout),
            displayOptions = displayOptions,
            coverSlotAspectRatio = coverSlotAspectRatios.getOrElse(position) { DefaultCoverAspectRatio },
            onClick = onBookClick,
            onLongClick = onBookLongClick,
        )
    }

    override fun onViewRecycled(holder: BookGridViewHolder) {
        holder.item.clear()
    }

    override fun getItemCount(): Int = books.size
}

private class BookGridViewHolder(val item: BookGridItemView) : RecyclerView.ViewHolder(item)

private fun List<LibraryBook>.coverSlotAspectRatios(columnCount: Int): FloatArray {
    val safeColumnCount = columnCount.coerceAtLeast(1)
    val result = FloatArray(size) { DefaultCoverAspectRatio }
    var rowStart = 0
    while (rowStart < size) {
        val rowEnd = (rowStart + safeColumnCount).coerceAtMost(size)
        val rowAspectRatio = (rowStart until rowEnd)
            .asSequence()
            .map { index ->
                get(index).coverPath()?.let(CoverAspectRatioCache::aspectRatio) ?: DefaultCoverAspectRatio
            }
            .filter { it > 0f }
            .minOrNull()
            ?: DefaultCoverAspectRatio
        for (index in rowStart until rowEnd) {
            result[index] = rowAspectRatio
        }
        rowStart = rowEnd
    }
    return result
}

private class BookGridItemView(context: Context) : LinearLayout(context) {
    private val coverSlot = AspectRatioFrameLayout(context)
    private val coverFrame = AspectRatioFrameLayout(context)
    private val coverImage = ImageView(context)
    private val placeholder = TextView(context)
    private val selectionBadge = SelectionBadgeView(context)
    private val statusMarker = StatusMarkerView(context)
    private val titleView = TextView(context)
    private val authorView = TextView(context)
    private val seriesView = TextView(context)
    private var imageDisposable: Disposable? = null

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        coverSlot.clipChildren = false
        coverSlot.clipToPadding = false
        coverFrame.clipToOutline = true
        coverFrame.addView(
            coverImage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        coverFrame.addView(
            placeholder,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        coverFrame.addView(
            selectionBadge,
            FrameLayout.LayoutParams(
                context.dpToPx(28.dp),
                context.dpToPx(28.dp),
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = context.dpToPx(8.dp)
                marginEnd = context.dpToPx(8.dp)
            },
        )
        coverFrame.addView(
            statusMarker,
            FrameLayout.LayoutParams(
                context.dpToPx(14.dp),
                context.dpToPx(14.dp),
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = context.dpToPx(8.dp)
                marginEnd = context.dpToPx(8.dp)
            },
        )
        coverSlot.addView(
            coverFrame,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        addView(
            coverSlot,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(titleView, textLayoutParams())
        addView(authorView, textLayoutParams())
        addView(seriesView, textLayoutParams())

        coverImage.scaleType = ImageView.ScaleType.FIT_CENTER
        placeholder.text = context.getString(R.string.cover_placeholder)
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        placeholder.typeface = Typeface.DEFAULT_BOLD
        placeholder.gravity = Gravity.CENTER

        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        titleView.typeface = context.cormorantGaramondTypeface(weight = 600)
        titleView.ellipsize = TextUtils.TruncateAt.END

        listOf(authorView, seriesView).forEach { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            view.ellipsize = TextUtils.TruncateAt.END
            view.maxLines = 1
        }
    }

    fun bind(
        item: LibraryBook,
        isSelected: Boolean,
        isBatchMode: Boolean,
        exposeBookSemantics: Boolean,
        style: BookGridNativeStyle,
        itemLayout: BookGridItemLayout,
        displayOptions: LibraryDisplayOptions,
        coverSlotAspectRatio: Float,
        onClick: (String) -> Unit,
        onLongClick: (String) -> Unit,
    ) {
        val viewContext = context
        val title = item.metadata.title
        val displayedAuthor = item.metadata.authors.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: viewContext.getString(R.string.book_unknown_author)
        val seriesLabel = item.metadata.seriesLabel()
        val coverPath = item.coverPath()
        val showTitle = displayOptions.showTitle
        val showAuthor = displayOptions.showAuthor
        val showSeries = displayOptions.showSeries && seriesLabel != null
        val showStatus = item.hasProminentGridStatus()

        setOnClickListener { onClick(item.book.id) }
        setOnLongClickListener {
            onLongClick(item.book.id)
            true
        }

        if (exposeBookSemantics) {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = listOfNotNull(
                title,
                displayedAuthor,
                seriesLabel,
            ).filter { it.isNotBlank() }.joinToString(separator = ". ")
        } else {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            contentDescription = null
        }

        coverFrame.background = roundedDrawable(
            color = style.placeholderBackgroundColor,
            radiusPx = viewContext.dpToPx(6.dp).toFloat(),
        )
        coverFrame.foreground = if (isSelected) {
            roundedDrawable(
                color = android.graphics.Color.TRANSPARENT,
                radiusPx = viewContext.dpToPx(6.dp).toFloat(),
                strokeColor = style.selectedColor,
                strokeWidthPx = viewContext.dpToPx(2.dp),
            )
        } else {
            null
        }

        titleView.text = title
        titleView.setTextColor(style.titleColor)
        titleView.maxLines = itemLayout.titleMaxLines

        authorView.text = displayedAuthor
        authorView.setTextColor(style.authorColor)

        seriesView.text = seriesLabel.orEmpty()
        seriesView.setTextColor(style.seriesColor)

        titleView.visibility = if (showTitle) View.VISIBLE else View.GONE
        authorView.visibility = if (showAuthor) View.VISIBLE else View.GONE
        seriesView.visibility = if (showSeries) View.VISIBLE else View.GONE
        applyTextSpacing(itemLayout.textTopSpacing, showTitle, showAuthor, showSeries)

        placeholder.setTextColor(style.placeholderTextColor)
        placeholder.visibility = if (coverPath == null) View.VISIBLE else View.GONE
        coverImage.visibility = if (coverPath == null) View.GONE else View.VISIBLE
        val coverAspectRatio = coverPath?.let(CoverAspectRatioCache::aspectRatio)
            ?: DefaultCoverAspectRatio
        coverSlot.setAspectRatio(coverSlotAspectRatio)
        coverFrame.setAspectRatio(coverAspectRatio)
        imageDisposable?.dispose()
        imageDisposable = null
        coverImage.setImageDrawable(null)
        if (coverPath != null) {
            val coverDecodeHeightPx = itemLayout.coverDecodeHeightPx(coverAspectRatio)
            val request = ImageRequest.Builder(viewContext)
                .data(File(coverPath))
                .target(coverImage)
                .size(itemLayout.coverDecodeWidthPx, coverDecodeHeightPx)
                .crossfade(false)
                .allowHardware(true)
                .build()
            imageDisposable = viewContext.imageLoader.enqueue(request)
        }

        selectionBadge.visibility = if (isBatchMode) View.VISIBLE else View.GONE
        selectionBadge.setState(
            selected = isSelected,
            selectedColor = style.selectedColor,
            selectedContentColor = style.selectedContentColor,
            surfaceColor = style.badgeSurfaceColor,
            outlineColor = style.badgeOutlineColor,
        )

        statusMarker.visibility = if (!isBatchMode && showStatus) View.VISIBLE else View.GONE
        statusMarker.setColors(
            fillColor = style.statusColor(item.book.status, item.book.syncStatus),
            strokeColor = style.badgeSurfaceColor,
        )
    }

    fun clear() {
        imageDisposable?.dispose()
        imageDisposable = null
        coverImage.setImageDrawable(null)
    }

    private fun applyTextSpacing(
        topSpacing: Dp,
        showTitle: Boolean,
        showAuthor: Boolean,
        showSeries: Boolean,
    ) {
        var firstVisibleApplied = false
        listOf(
            titleView to showTitle,
            authorView to showAuthor,
            seriesView to showSeries,
        ).forEach { (view, visible) ->
            val params = view.layoutParams as LinearLayout.LayoutParams
            params.topMargin = if (visible && !firstVisibleApplied) {
                context.dpToPx(topSpacing)
            } else {
                0
            }
            view.layoutParams = params
            if (visible) {
                firstVisibleApplied = true
            }
        }
    }

    private fun textLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}

private class AspectRatioFrameLayout(context: Context) : FrameLayout(context) {
    private var aspectRatio: Float = DefaultCoverAspectRatio

    fun setAspectRatio(nextAspectRatio: Float) {
        val sanitizedAspectRatio = nextAspectRatio.takeIf { it > 0f } ?: DefaultCoverAspectRatio
        if (aspectRatio == sanitizedAspectRatio) return
        aspectRatio = sanitizedAspectRatio
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspectRatio).roundToInt()
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}

private object CoverAspectRatioCache {
    private val cache = LruCache<String, Float>(512)

    fun aspectRatio(path: String): Float {
        cache.get(path)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        val ratio = if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            DefaultCoverAspectRatio
        }
        cache.put(path, ratio)
        return ratio
    }
}

private class SelectionBadgeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected = false
    private var selectedColor = android.graphics.Color.BLACK
    private var selectedContentColor = android.graphics.Color.WHITE
    private var surfaceColor = android.graphics.Color.WHITE
    private var outlineColor = android.graphics.Color.GRAY

    fun setState(
        selected: Boolean,
        selectedColor: Int,
        selectedContentColor: Int,
        surfaceColor: Int,
        outlineColor: Int,
    ) {
        this.selected = selected
        this.selectedColor = selectedColor
        this.selectedContentColor = selectedContentColor
        this.surfaceColor = surfaceColor
        this.outlineColor = outlineColor
        invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val radius = width.coerceAtMost(height) / 2f
        paint.style = Paint.Style.FILL
        paint.color = if (selected) selectedColor else surfaceColor
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpToPx(1.dp).toFloat()
        paint.color = if (selected) selectedColor else outlineColor
        canvas.drawCircle(width / 2f, height / 2f, radius - paint.strokeWidth / 2f, paint)

        if (selected) {
            paint.color = selectedContentColor
            paint.strokeWidth = context.dpToPx(2.dp).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(width * 0.30f, height * 0.52f, width * 0.45f, height * 0.67f, paint)
            canvas.drawLine(width * 0.45f, height * 0.67f, width * 0.72f, height * 0.34f, paint)
        }
    }
}

private class StatusMarkerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fillColor = android.graphics.Color.BLACK
    private var strokeColor = android.graphics.Color.WHITE

    fun setColors(fillColor: Int, strokeColor: Int) {
        this.fillColor = fillColor
        this.strokeColor = strokeColor
        invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val radius = width.coerceAtMost(height) / 2f
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpToPx(1.dp).toFloat()
        paint.color = strokeColor
        canvas.drawCircle(width / 2f, height / 2f, radius - paint.strokeWidth / 2f, paint)
    }
}

private class BookGridSpacingDecoration(
    private var spanCount: Int,
    private var horizontalSpacingPx: Int,
    private var verticalSpacingPx: Int,
) : RecyclerView.ItemDecoration() {
    fun update(
        spanCount: Int,
        horizontalSpacingPx: Int,
        verticalSpacingPx: Int,
    ) {
        this.spanCount = spanCount.coerceAtLeast(1)
        this.horizontalSpacingPx = horizontalSpacingPx
        this.verticalSpacingPx = verticalSpacingPx
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount
        outRect.left = column * horizontalSpacingPx / spanCount
        outRect.right = horizontalSpacingPx - (column + 1) * horizontalSpacingPx / spanCount
        outRect.bottom = verticalSpacingPx
    }
}

private fun roundedDrawable(
    color: Int,
    radiusPx: Float,
    strokeColor: Int? = null,
    strokeWidthPx: Int = 0,
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokeColor != null && strokeWidthPx > 0) {
            setStroke(strokeWidthPx, strokeColor)
        }
    }
}

private fun Context.dpToPx(dp: Dp): Int {
    return (dp.value * resources.displayMetrics.density).roundToInt()
}

private fun Context.cormorantGaramondTypeface(weight: Int): Typeface {
    val base = ResourcesCompat.getFont(this, R.font.cormorant_garamond_wght) ?: Typeface.SERIF
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, Typeface.NORMAL)
    }
}

@Composable
private fun BookCoverItem(
    item: LibraryBook,
    itemLayout: BookGridItemLayout,
    exposeBookSemantics: Boolean,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val title = item.metadata.title
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val author = item.metadata.authors.firstOrNull().orEmpty()
    val needsAuthor = itemLayout.showAuthor || exposeBookSemantics
    val needsSeries = itemLayout.showSeries || exposeBookSemantics
    val showCompactStatus = item.hasProminentGridStatus()
    val needsStatus = showCompactStatus || exposeBookSemantics
    val displayedAuthor = if (needsAuthor) {
        author.ifBlank { stringResource(R.string.book_unknown_author) }
    } else {
        ""
    }
    val seriesLabel = if (needsSeries) item.metadata.seriesLabel() else null
    val coverPath = item.coverPath()
    val coverAspectRatio = remember(coverPath) {
        coverPath?.let(CoverAspectRatioCache::aspectRatio) ?: DefaultCoverAspectRatio
    }
    val statusLabel = if (needsStatus) {
        bookStatusLabel(status = item.book.status, syncStatus = item.book.syncStatus)
    } else {
        null
    }
    val accessibilityLabel = remember(title, displayedAuthor, seriesLabel, statusLabel) {
        listOfNotNull(title, displayedAuthor, seriesLabel, statusLabel.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(separator = ". ")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (exposeBookSemantics) {
                    Modifier.clearAndSetSemantics {
                        contentDescription = accessibilityLabel
                        role = Role.Button
                        onClick {
                            currentOnClick()
                            true
                        }
                        onLongClick {
                            currentOnLongClick()
                            true
                        }
                        if (isBatchMode) {
                            selected = isSelected
                        }
                    }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { currentOnLongClick() },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (coverPath != null) {
                val coverRequest = remember(context, coverPath, itemLayout.coverDecodeWidthPx) {
                    ImageRequest.Builder(context)
                        .data(File(coverPath))
                        .size(itemLayout.coverDecodeWidthPx, itemLayout.coverDecodeHeightPx(coverAspectRatio))
                        .crossfade(false)
                        .allowHardware(true)
                        .build()
                }

                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    alignment = Alignment.BottomCenter,
                    contentScale = ContentScale.Fit,
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
            } else if (showCompactStatus) {
                CompactStatusMarker(
                    status = item.book.status,
                    syncStatus = item.book.syncStatus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(itemLayout.textTopSpacing))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = itemLayout.titleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (itemLayout.showAuthor) {
            Text(
                text = displayedAuthor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (itemLayout.showSeries && seriesLabel != null) {
            Text(
                text = seriesLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
private fun BatchDeleteDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.batch_delete_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = selectedCountText(selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = stringResource(R.string.batch_delete_dialog_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.delete_book_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.delete_book_cancel))
            }
        },
    )
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
    val label = bookStatusLabel(status = status, syncStatus = syncStatus)
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
private fun BookGridStatusPill(
    status: BookStatus,
    syncStatus: SyncStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        syncStatus == SyncStatus.CONFLICT || syncStatus == SyncStatus.SYNC_ERROR -> MaterialTheme.colorScheme.error
        status == BookStatus.ERROR -> MaterialTheme.colorScheme.error
        syncStatus == SyncStatus.SYNCED -> MaterialTheme.colorScheme.secondary
        status == BookStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .border(
                width = 0.75.dp,
                color = tint.copy(alpha = 0.48f),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label.uppercase(),
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
private fun CompactStatusMarker(
    status: BookStatus,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        syncStatus == SyncStatus.CONFLICT || syncStatus == SyncStatus.SYNC_ERROR -> MaterialTheme.colorScheme.error
        status == BookStatus.ERROR -> MaterialTheme.colorScheme.error
        status == BookStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(14.dp)
            .background(
                color = tint.copy(alpha = 0.92f),
                shape = RoundedCornerShape(7.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(7.dp),
            ),
    )
}

private data class BookGridItemLayout(
    val columnCount: Int,
    val coverDecodeWidthPx: Int,
    val titleMaxLines: Int,
    val showAuthor: Boolean,
    val showSeries: Boolean,
    val textTopSpacing: Dp,
) {
    fun coverDecodeHeightPx(aspectRatio: Float): Int {
        val sanitizedAspectRatio = aspectRatio.takeIf { it > 0f } ?: DefaultCoverAspectRatio
        return (coverDecodeWidthPx / sanitizedAspectRatio).roundToInt()
    }

    companion object {
        fun forColumnCount(columnCount: Int): BookGridItemLayout {
            return when {
                columnCount >= 5 -> BookGridItemLayout(
                    columnCount = columnCount,
                    coverDecodeWidthPx = 220,
                    titleMaxLines = 2,
                    showAuthor = true,
                    showSeries = true,
                    textTopSpacing = 6.dp,
                )
                columnCount == 4 -> BookGridItemLayout(
                    columnCount = columnCount,
                    coverDecodeWidthPx = 280,
                    titleMaxLines = 2,
                    showAuthor = true,
                    showSeries = true,
                    textTopSpacing = 7.dp,
                )
                else -> BookGridItemLayout(
                    columnCount = columnCount,
                    coverDecodeWidthPx = 420,
                    titleMaxLines = 2,
                    showAuthor = true,
                    showSeries = true,
                    textTopSpacing = 8.dp,
                )
            }
        }
    }
}

@Composable
private fun bookStatusLabel(
    status: BookStatus,
    syncStatus: SyncStatus,
): String {
    return when {
        syncStatus == SyncStatus.CONFLICT -> stringResource(R.string.status_conflict)
        syncStatus == SyncStatus.SYNC_ERROR -> stringResource(R.string.status_sync_error)
        syncStatus == SyncStatus.SYNCED -> stringResource(R.string.status_synced)
        syncStatus == SyncStatus.PENDING_UPLOAD -> stringResource(R.string.status_pending_upload)
        syncStatus == SyncStatus.PENDING_DOWNLOAD -> stringResource(R.string.status_pending_download)
        else -> stringResource(status.labelRes())
    }
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.46f),
                shape = RoundedCornerShape(6.dp),
            )
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
fun EditorialDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 28.dp,
) {
    Row(
        modifier = modifier.padding(horizontal = horizontalPadding),
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
private const val BookGridAutoMaxColumns = 3
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

private fun LibraryBook.coverPath(): String? {
    return book.coverThumbPath ?: book.coverImagePath
}

private fun LibraryBook.hasProminentGridStatus(): Boolean {
    return book.syncStatus == SyncStatus.CONFLICT ||
        book.syncStatus == SyncStatus.SYNC_ERROR ||
        book.syncStatus == SyncStatus.PENDING_UPLOAD ||
        book.syncStatus == SyncStatus.PENDING_DOWNLOAD ||
        book.status == BookStatus.ERROR ||
        book.status == BookStatus.NEEDS_REVIEW
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

private fun BookGridColumns.columnCount(
    availableWidth: Dp,
    horizontalPadding: Dp,
    horizontalSpacing: Dp,
): Int {
    fixedCount?.let { return it }

    val contentWidth = (availableWidth - (horizontalPadding * 2)).value
        .coerceAtLeast(BookGridMinCellWidth.value)
    val columnWidthWithGap = BookGridMinCellWidth.value + horizontalSpacing.value
    return ((contentWidth + horizontalSpacing.value) / columnWidthWithGap)
        .toInt()
        .coerceAtLeast(1)
        .coerceAtMost(BookGridAutoMaxColumns)
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
