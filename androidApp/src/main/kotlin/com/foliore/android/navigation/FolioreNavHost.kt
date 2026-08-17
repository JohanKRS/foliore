package com.foliore.android.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.foliore.android.R
import com.foliore.android.storage.AppLanguage
import com.foliore.android.ui.details.BookDetailsRoute
import com.foliore.android.ui.details.EditBottomBarActions
import com.foliore.android.ui.details.EditMetadataRoute
import com.foliore.android.ui.details.TabletEditLeftPaneWeight
import com.foliore.android.ui.details.TabletEditRightPaneWeight
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.BookGridScrollState
import com.foliore.android.ui.library.LibraryTopChromeOverlay
import com.foliore.android.ui.library.LibraryRoute
import com.foliore.android.ui.library.GoogleDriveRemoteLibraryDialog
import com.foliore.android.ui.library.SettingsRoute
import com.foliore.android.ui.library.SyncProgressDivider
import com.foliore.android.ui.library.SyncProgressStatusLine
import com.foliore.android.ui.TopChromeContent
import com.foliore.android.ui.TopChromeHeaderHeight
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.ui.tuning.LocalVisualTuning
import com.foliore.android.viewmodel.LibraryViewModel
import com.foliore.android.viewmodel.SyncProgress
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState

private val BottomBarHeight = 94.dp
private val BottomBarContentInset = BottomBarHeight + 28.dp
private val BottomBarItemTopPadding = 8.dp
private val BottomBarItemHeight = 56.dp
private val BottomBarItemBottomPadding = 30.dp
private val BottomEditActionStripHeight = 58.dp
private val BottomEditActionStripButtonHeight = 44.dp
private val BottomEditContentInset = BottomBarHeight + BottomEditActionStripHeight + 48.dp
private val BottomNavigationMaxWidth = 560.dp
private val BottomNavigationHorizontalPadding = 28.dp
private val BottomNavigationTopLevelSpacing = 18.dp
private val BottomNavigationEditSpacing = 10.dp
private const val RouteTransitionMillis = 360
@Composable
fun FolioreNavHost(
    libraryViewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
) {
    val navController = rememberNavController()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val currentBookId = backStackEntry?.arguments?.getString(Routes.ARG_BOOK_ID)
    var editBottomBarState by remember { mutableStateOf<EditBottomBarState?>(null) }
    var editTopChromeContent by remember { mutableStateOf<TopChromeContent?>(null) }
    var topChromeHeightPx by remember { mutableStateOf(0) }
    val libraryChromeHazeState = rememberHazeState()
    val editChromeHazeState = rememberHazeState()
    val libraryGridScrollState = rememberSaveable(saver = BookGridScrollState.Saver) {
        BookGridScrollState()
    }
    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = libraryViewModel::importUris,
    )
    var launchGoogleDriveResolution: (android.app.PendingIntent) -> Unit = {}
    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        libraryViewModel.completeGoogleDriveAuthorization(
            result.data,
            launchGoogleDriveResolution,
        )
    }
    launchGoogleDriveResolution = { pendingIntent ->
        googleDriveAuthorizationLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val openTabletWorkspace = maxWidth >= 840.dp
        val showTopLevelNavigation = currentRoute in TopLevelDestination.routes
        val showEditActions = currentRoute == Routes.EDIT
        val showEditNavigation = showEditActions && openTabletWorkspace
        val showNavigation = showTopLevelNavigation || showEditActions
        val editActions = editBottomBarState
            ?.takeIf { showEditActions && it.bookId == currentBookId }
            ?.actions
        val topChromeMode = when (currentRoute) {
            Routes.LIBRARY -> TopChromeMode.Library
            Routes.EDIT -> TopChromeMode.Edit
            else -> TopChromeMode.None
        }
        val tuning = LocalVisualTuning.current
        val topChromeHeaderHeight = tuning.topChromeHeaderHeight.dp
        val bottomBarHeight = tuning.bottomBarHeight.dp
        val bottomBarContentInset = bottomBarHeight + tuning.bottomBarContentExtra.dp
        val bottomEditActionStripHeight = tuning.bottomEditActionStripHeight.dp
        val bottomEditContentInset = bottomBarHeight + bottomEditActionStripHeight + tuning.bottomEditContentExtra.dp
        val activeChromeHazeState = when (topChromeMode) {
            TopChromeMode.Edit -> editChromeHazeState
            else -> libraryChromeHazeState
        }
        val density = LocalDensity.current
        val topSystemBarInset = with(density) { WindowInsets.systemBars.getTop(this).toDp() }
        val bottomSystemBarInset = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }
        val measuredTopChromeInset = with(density) { topChromeHeightPx.toDp() }
        val topChromeInset = if (topChromeMode == TopChromeMode.None) {
            0.dp
        } else if (measuredTopChromeInset.value > 0f) {
            measuredTopChromeInset
        } else {
            topSystemBarInset + topChromeHeaderHeight
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                if (showNavigation) {
                    FolioreNavigationBar(
                        selectedRoute = currentRoute,
                        mode = if (showEditNavigation) {
                            BottomBarMode.Edit
                        } else if (showEditActions) {
                            BottomBarMode.EditStacked
                        } else {
                            BottomBarMode.TopLevel
                        },
                        editActions = editActions,
                        isLoading = libraryState.hasActiveProgress,
                        progress = libraryState.activeProgress,
                        tabletWorkspace = openTabletWorkspace,
                        hazeState = activeChromeHazeState,
                        onLibraryClick = { navController.returnToLibrary() },
                        onSyncClick = libraryViewModel::syncNow,
                        onSettingsClick = { navController.navigateTopLevel(Routes.SETTINGS) },
                    )
                }
            },
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = when {
                !showNavigation -> PaddingValues(0.dp)
                currentRoute == Routes.LIBRARY || currentRoute == Routes.EDIT -> PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = 0.dp,
                )
                else -> paddingValues
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(contentPadding)
                    .padding(contentPadding),
            ) {
                NavContent(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    libraryViewModel = libraryViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    appLanguage = appLanguage,
                    onAppLanguageChange = onAppLanguageChange,
                    onEditBook = { bookId -> navController.navigate(Routes.edit(bookId)) },
                    onBack = { navController.popBackStack() },
                    onEditBottomBarActionsChange = { actions ->
                        editBottomBarState = actions?.let {
                            EditBottomBarState(
                                bookId = currentBookId.orEmpty(),
                                actions = it,
                            )
                        }
                    },
                    onEditTopChromeContentChange = { content ->
                        editTopChromeContent = content
                    },
                    libraryGridScrollState = libraryGridScrollState,
                    libraryChromeHazeState = libraryChromeHazeState,
                    editChromeHazeState = editChromeHazeState,
                    topChromeInset = topChromeInset,
                    bottomChromeInset = when {
                        showNavigation && currentRoute == Routes.LIBRARY -> bottomBarContentInset + bottomSystemBarInset
                        showNavigation && currentRoute == Routes.EDIT -> bottomEditContentInset + bottomSystemBarInset
                        else -> 0.dp
                    },
                )

                if (topChromeMode != TopChromeMode.None) {
                    TopChromeGlassContainer(
                        hazeState = activeChromeHazeState,
                        resetKey = topChromeMode to currentBookId,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    topChromeHeightPx = size.height
                                }
                            },
                    ) {
                        Crossfade(
                            targetState = topChromeMode,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            label = "Top chrome fade",
                            modifier = Modifier.fillMaxWidth(),
                        ) { mode ->
                            when (mode) {
                                TopChromeMode.Library -> {
                                    LibraryTopChromeOverlay(
                                        state = libraryState,
                                        hazeState = libraryChromeHazeState,
                                        onImportClick = {
                                            epubPicker.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                                        },
                                        onSearchClick = { libraryViewModel.setSearchOpen(true) },
                                        onCloseSearch = { libraryViewModel.setSearchOpen(false) },
                                        onQueryChange = libraryViewModel::setQuery,
                                        onFilterChange = libraryViewModel::setFilter,
                                        onSortOrderChange = libraryViewModel::setSortOrder,
                                        onSeriesFilterChange = libraryViewModel::setSeriesFilter,
                                        onGridColumnsChange = libraryViewModel::setGridColumns,
                                        onShowBookTitleChange = libraryViewModel::setShowBookTitle,
                                        onShowBookAuthorChange = libraryViewModel::setShowBookAuthor,
                                        onShowBookSeriesChange = libraryViewModel::setShowBookSeries,
                                        onBatchModeChange = libraryViewModel::setBatchMode,
                                        onSelectBooks = libraryViewModel::selectBooks,
                                        onBatchUpdate = libraryViewModel::applyBatchMetadata,
                                        onBatchDelete = libraryViewModel::deleteSelectedBooks,
                                        modifier = Modifier.fillMaxWidth(),
                                        drawBackground = false,
                                    )
                                }
                                TopChromeMode.Edit -> {
                                    editTopChromeContent?.invoke(Modifier.fillMaxWidth())
                                        ?: Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(topChromeHeaderHeight),
                                        )
                                }
                                TopChromeMode.None -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    libraryState.googleDriveRemoteLibraryRecovery?.let { recovery ->
        GoogleDriveRemoteLibraryDialog(
            recovery = recovery,
            isWorking = libraryState.isSyncing || libraryState.isGoogleDriveBackupStatsLoading,
            progress = libraryState.syncProgress,
            onRecreateOnDrive = libraryViewModel::recreateGoogleDriveLibrary,
            onKeepOnlyOnDevice = {
                libraryViewModel.clearGoogleDriveAndDisconnect(launchGoogleDriveResolution)
            },
            onDismiss = libraryViewModel::dismissGoogleDriveRemoteLibraryRecovery,
        )
    }
}

@Composable
private fun NavContent(
    modifier: Modifier,
    navController: NavHostController,
    libraryViewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onEditBook: (String) -> Unit,
    onBack: () -> Unit,
    onEditBottomBarActionsChange: (EditBottomBarActions?) -> Unit,
    onEditTopChromeContentChange: (TopChromeContent?) -> Unit,
    libraryGridScrollState: BookGridScrollState,
    libraryChromeHazeState: HazeState,
    editChromeHazeState: HazeState,
    topChromeInset: Dp,
    bottomChromeInset: androidx.compose.ui.unit.Dp,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier.fillMaxSize(),
        enterTransition = { routeEnterTransition() },
        exitTransition = { routeExitTransition() },
        popEnterTransition = { routeEnterTransition() },
        popExitTransition = { routeExitTransition() },
    ) {
        composable(Routes.LIBRARY) {
            LibraryRoute(
                viewModel = libraryViewModel,
                onBookClick = { bookId -> navController.navigate(Routes.edit(bookId)) },
                onSettingsClick = { navController.navigateTopLevel(Routes.SETTINGS) },
                gridScrollState = libraryGridScrollState,
                chromeHazeState = libraryChromeHazeState,
                topChromeInset = topChromeInset,
                bottomChromeInset = bottomChromeInset,
                showTopChrome = false,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                viewModel = libraryViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                appLanguage = appLanguage,
                onAppLanguageChange = onAppLanguageChange,
                bottomChromeInset = bottomChromeInset,
            )
        }
        composable(Routes.DETAILS) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            BookDetailsRoute(
                viewModel = libraryViewModel,
                bookId = bookId,
                onEditMetadata = { onEditBook(bookId) },
                onBack = onBack,
                bottomChromeInset = bottomChromeInset,
            )
        }
        composable(Routes.EDIT) { backStackEntry ->
            EditMetadataRoute(
                viewModel = libraryViewModel,
                bookId = backStackEntry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty(),
                onBack = onBack,
                onBottomBarActionsChange = onEditBottomBarActionsChange,
                chromeHazeState = editChromeHazeState,
                topChromeInset = topChromeInset,
                bottomChromeInset = bottomChromeInset,
                showTopChrome = false,
                onTopChromeContentChange = onEditTopChromeContentChange,
            )
        }
    }
}

@Composable
private fun TopChromeGlassContainer(
    hazeState: HazeState,
    resetKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val chromeTint = MaterialTheme.colorScheme.background

    Box(modifier = modifier) {
        key(resetKey) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .hazeEffect(state = hazeState) {
                        inputScale = HazeInputScale.Fixed(0.5f)
                        blurEffect {
                            blurRadius = 40.dp
                            colorEffects = listOf(
                                HazeColorEffect.tint(chromeTint.copy(alpha = 0.74f)),
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
                                0.48f to chromeTint.copy(alpha = 0.94f),
                                0.78f to chromeTint.copy(alpha = 0.82f),
                                0.93f to chromeTint.copy(alpha = 0.58f),
                                1f to chromeTint.copy(alpha = 0.34f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top),
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun FolioreNavigationBar(
    selectedRoute: String?,
    mode: BottomBarMode,
    editActions: EditBottomBarActions?,
    isLoading: Boolean,
    progress: SyncProgress?,
    tabletWorkspace: Boolean,
    hazeState: HazeState,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val editMode = mode != BottomBarMode.TopLevel
    val splitEditMode = mode == BottomBarMode.Edit
    val stackedEditMode = mode == BottomBarMode.EditStacked
    val navigationSelectedRoute = if (editMode) Routes.LIBRARY else selectedRoute
    val chromeTint = MaterialTheme.colorScheme.background
    val mobileChrome = !tabletWorkspace
    val tuning = LocalVisualTuning.current
    val bottomBarHeight = tuning.bottomBarHeight.dp
    val bottomEditActionStripHeight = tuning.bottomEditActionStripHeight.dp
    val chromeModifier = Modifier
        .fillMaxWidth()
        .then(
            if (tabletWorkspace) {
                Modifier.animateContentSize(animationSpec = tween(durationMillis = 180))
            } else {
                Modifier
            },
        )

    Column(
        modifier = chromeModifier
            .hazeEffect(state = hazeState) {
                inputScale = HazeInputScale.Fixed(0.5f)
                blurEffect {
                    blurRadius = 40.dp
                    colorEffects = listOf(
                        HazeColorEffect.tint(chromeTint.copy(alpha = 0.7f)),
                    )
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 0f,
                        endIntensity = 1f,
                    )
                }
            }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to chromeTint.copy(alpha = 0.38f),
                        0.22f to chromeTint.copy(alpha = 0.68f),
                        0.58f to chromeTint.copy(alpha = 0.92f),
                        1f to chromeTint.copy(alpha = 0.98f),
                    ),
                ),
            ),
    ) {
        if (mobileChrome) {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    ) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        initialOffsetY = { height -> height },
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    ) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> height },
                    ),
            ) {
                SyncStatusBlock(progress = progress)
            }
        } else if (isLoading) {
            SyncStatusBlock(progress = progress)
        }

        SyncProgressDivider(
            isActive = isLoading,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mobileChrome) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
            ) {
                AnimatedVisibility(
                    visible = stackedEditMode,
                    enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        ) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> height },
                        ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                        shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        ) +
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                            targetOffsetY = { height -> height },
                        ),
                ) {
                    EditActionsSlot(
                        editActions = editActions,
                        isLoading = isLoading,
                        compact = true,
                        delayMillis = 0,
                        actionStrip = true,
                        animated = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomEditActionStripHeight),
                    )
                }
                StaticNavigationItemsSlot(
                    selectedRoute = navigationSelectedRoute,
                    isLoading = isLoading,
                    onLibraryClick = onLibraryClick,
                    onSyncClick = onSyncClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomBarHeight),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomBarHeight),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val dividerWidth = 1.dp
                        val splitNavigationWidth = (maxWidth - dividerWidth) *
                            (TabletEditLeftPaneWeight / (TabletEditLeftPaneWeight + TabletEditRightPaneWeight))

                        AnimatedNavigationItemsSlot(
                            selectedRoute = navigationSelectedRoute,
                            splitEditMode = splitEditMode,
                            splitNavigationWidth = if (splitEditMode) splitNavigationWidth else maxWidth,
                            isLoading = isLoading,
                            onLibraryClick = onLibraryClick,
                            onSyncClick = onSyncClick,
                            onSettingsClick = onSettingsClick,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (splitEditMode) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Spacer(
                                    modifier = Modifier
                                        .weight(TabletEditLeftPaneWeight)
                                        .fillMaxHeight(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(dividerWidth)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
                                )
                                EditActionsSlot(
                                    editActions = editActions,
                                    isLoading = isLoading,
                                    compact = editActions?.compact == true,
                                    delayMillis = 220,
                                    modifier = Modifier
                                        .weight(TabletEditRightPaneWeight)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusBlock(
    progress: SyncProgress?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        SyncProgressStatusLine(
            progress = progress,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun AnimatedNavigationItemsSlot(
    selectedRoute: String?,
    splitEditMode: Boolean,
    splitNavigationWidth: Dp,
    isLoading: Boolean,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryDestination = TopLevelDestination.items.first()
    val settingsDestination = TopLevelDestination.items.last()
    val transition = updateTransition(targetState = splitEditMode, label = "bottomNavSplitMode")
    val tuning = LocalVisualTuning.current
    val bottomNavigationMaxWidth = tuning.bottomTabletNavigationMaxWidth.dp
    val bottomNavigationHorizontalPadding = tuning.bottomTabletNavigationHorizontalPadding.dp
    val bottomNavigationTopLevelSpacing = tuning.bottomTabletNavigationTopLevelSpacing.dp
    val bottomNavigationEditSpacing = tuning.bottomTabletNavigationEditSpacing.dp
    val itemTopPadding = tuning.bottomTabletBarItemTopPadding.dp
    val itemHeight = tuning.bottomTabletBarItemHeight.dp
    val iconSize = tuning.bottomTabletNavigationIconSize.dp
    val labelSpacing = tuning.bottomTabletNavigationLabelSpacing.dp

    BoxWithConstraints(modifier = modifier) {
        val topRowWidth = if (maxWidth < bottomNavigationMaxWidth) {
            maxWidth
        } else {
            bottomNavigationMaxWidth
        }
        val splitRowWidth = if (splitNavigationWidth < bottomNavigationMaxWidth) {
            splitNavigationWidth
        } else {
            bottomNavigationMaxWidth
        }
        val topRowStart = (maxWidth - topRowWidth) / 2f
        val splitRowStart = (splitNavigationWidth - splitRowWidth) / 2f
        val topContentWidth = topRowWidth - (bottomNavigationHorizontalPadding * 2f)
        val topItemWidth = (topContentWidth - (bottomNavigationTopLevelSpacing * 2f)) / 3f
        val splitContentWidth = splitRowWidth - (bottomNavigationHorizontalPadding * 2f)
        val splitItemWidth = (splitContentWidth - (bottomNavigationEditSpacing * 2f)) / 3f

        AnimatedNavigationItem(
            index = 0,
            transition = transition,
            topStart = topRowStart + bottomNavigationHorizontalPadding,
            splitStart = splitRowStart + bottomNavigationHorizontalPadding,
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = libraryDestination.icon,
            label = stringResource(libraryDestination.labelRes),
            contentDescription = stringResource(libraryDestination.labelRes),
            selected = selectedRoute == libraryDestination.route || splitEditMode,
            itemTopPadding = itemTopPadding,
            itemHeight = itemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onLibraryClick,
        )
        AnimatedNavigationItem(
            index = 1,
            transition = transition,
            topStart = topRowStart + bottomNavigationHorizontalPadding + topItemWidth + bottomNavigationTopLevelSpacing,
            splitStart = splitRowStart + bottomNavigationHorizontalPadding + splitItemWidth + bottomNavigationEditSpacing,
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = Icons.Outlined.Sync,
            label = stringResource(R.string.nav_sync),
            contentDescription = stringResource(R.string.action_sync_now),
            selected = false,
            enabled = !isLoading,
            itemTopPadding = itemTopPadding,
            itemHeight = itemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onSyncClick,
        )
        AnimatedNavigationItem(
            index = 2,
            transition = transition,
            topStart = topRowStart + bottomNavigationHorizontalPadding +
                ((topItemWidth + bottomNavigationTopLevelSpacing) * 2f),
            splitStart = splitRowStart + bottomNavigationHorizontalPadding +
                ((splitItemWidth + bottomNavigationEditSpacing) * 2f),
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = settingsDestination.icon,
            label = stringResource(settingsDestination.labelRes),
            contentDescription = stringResource(settingsDestination.labelRes),
            selected = selectedRoute == settingsDestination.route,
            itemTopPadding = itemTopPadding,
            itemHeight = itemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun StaticNavigationItemsSlot(
    selectedRoute: String?,
    isLoading: Boolean,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryDestination = TopLevelDestination.items.first()
    val settingsDestination = TopLevelDestination.items.last()
    val tuning = LocalVisualTuning.current
    val bottomNavigationMaxWidth = tuning.bottomNavigationMaxWidth.dp
    val bottomNavigationHorizontalPadding = tuning.bottomNavigationHorizontalPadding.dp
    val bottomNavigationTopLevelSpacing = tuning.bottomNavigationTopLevelSpacing.dp
    val bottomBarItemTopPadding = tuning.bottomBarItemTopPadding.dp
    val bottomBarItemHeight = tuning.bottomBarItemHeight.dp
    val iconSize = tuning.bottomNavigationIconSize.dp
    val labelSpacing = tuning.bottomNavigationLabelSpacing.dp

    BoxWithConstraints(modifier = modifier) {
        val rowWidth = if (maxWidth < bottomNavigationMaxWidth) {
            maxWidth
        } else {
            bottomNavigationMaxWidth
        }
        val rowStart = (maxWidth - rowWidth) / 2f
        val contentWidth = rowWidth - (bottomNavigationHorizontalPadding * 2f)
        val itemWidth = (contentWidth - (bottomNavigationTopLevelSpacing * 2f)) / 3f

        FolioreBottomNavigationItem(
            icon = libraryDestination.icon,
            label = stringResource(libraryDestination.labelRes),
            contentDescription = stringResource(libraryDestination.labelRes),
            selected = selectedRoute == libraryDestination.route,
            itemHeight = bottomBarItemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onLibraryClick,
            modifier = Modifier
                .offset(x = rowStart + bottomNavigationHorizontalPadding, y = bottomBarItemTopPadding)
                .width(itemWidth),
        )
        FolioreBottomNavigationItem(
            icon = Icons.Outlined.Sync,
            label = stringResource(R.string.nav_sync),
            contentDescription = stringResource(R.string.action_sync_now),
            selected = false,
            enabled = !isLoading,
            itemHeight = bottomBarItemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onSyncClick,
            modifier = Modifier
                .offset(
                    x = rowStart + bottomNavigationHorizontalPadding + itemWidth + bottomNavigationTopLevelSpacing,
                    y = bottomBarItemTopPadding,
                )
                .width(itemWidth),
        )
        FolioreBottomNavigationItem(
            icon = settingsDestination.icon,
            label = stringResource(settingsDestination.labelRes),
            contentDescription = stringResource(settingsDestination.labelRes),
            selected = selectedRoute == settingsDestination.route,
            itemHeight = bottomBarItemHeight,
            iconSize = iconSize,
            labelSpacing = labelSpacing,
            onClick = onSettingsClick,
            modifier = Modifier
                .offset(
                    x = rowStart + bottomNavigationHorizontalPadding +
                        ((itemWidth + bottomNavigationTopLevelSpacing) * 2f),
                    y = bottomBarItemTopPadding,
                )
                .width(itemWidth),
        )
    }
}

@Composable
private fun AnimatedNavigationItem(
    index: Int,
    transition: androidx.compose.animation.core.Transition<Boolean>,
    topStart: Dp,
    splitStart: Dp,
    topWidth: Dp,
    splitWidth: Dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean = true,
    itemTopPadding: Dp,
    itemHeight: Dp,
    iconSize: Dp,
    labelSpacing: Dp,
    onClick: () -> Unit,
) {
    val x by transition.animateDp(
        transitionSpec = {
            val itemDelayMillis = if (targetState) {
                index * 54
            } else {
                (2 - index) * 54
            }
            tween(
                durationMillis = 360,
                delayMillis = itemDelayMillis,
                easing = FastOutSlowInEasing,
            )
        },
        label = "bottomNavItemX$index",
    ) { isSplit ->
        if (isSplit) splitStart else topStart
    }
    val width by transition.animateDp(
        transitionSpec = {
            val itemDelayMillis = if (targetState) {
                index * 54
            } else {
                (2 - index) * 54
            }
            tween(
                durationMillis = 360,
                delayMillis = itemDelayMillis,
                easing = FastOutSlowInEasing,
            )
        },
        label = "bottomNavItemWidth$index",
    ) { isSplit ->
        if (isSplit) splitWidth else topWidth
    }

    FolioreBottomNavigationItem(
        icon = icon,
        label = label,
        contentDescription = contentDescription,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .offset(x = x, y = itemTopPadding)
            .width(width),
        itemHeight = itemHeight,
        iconSize = iconSize,
        labelSpacing = labelSpacing,
    )
}

@Composable
private fun EditActionsSlot(
    editActions: EditBottomBarActions?,
    isLoading: Boolean,
    compact: Boolean,
    delayMillis: Int,
    actionStrip: Boolean = false,
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (animated) {
            AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 220,
                    delayMillis = delayMillis,
                ),
            ),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            FolioreEditActions(
                actions = editActions,
                isLoading = isLoading,
                compact = compact,
                actionStrip = actionStrip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        } else {
            FolioreEditActions(
                actions = editActions,
                isLoading = isLoading,
                compact = compact,
                actionStrip = actionStrip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FolioreEditActions(
    actions: EditBottomBarActions?,
    isLoading: Boolean,
    compact: Boolean,
    actionStrip: Boolean,
    modifier: Modifier = Modifier,
) {
    val exportLabel = stringResource(R.string.edit_metadata_export_short)
    val actionsEnabled = actions != null && !isLoading
    val saveEnabled = actions?.saveEnabled == true && !isLoading
    val saveHasPendingChanges = actions?.hasPendingSave == true && !isLoading
    val neutralButtonContainer = MaterialTheme.colorScheme.background
    val neutralButtonContent = MaterialTheme.colorScheme.primary
    val neutralButtonBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    val disabledButtonContent = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val tuning = LocalVisualTuning.current
    val horizontalPadding = if (actionStrip) {
        tuning.bottomActionHorizontalPadding.dp
    } else if (compact) {
        tuning.bottomTabletCompactActionHorizontalPadding.dp
    } else {
        tuning.bottomTabletActionHorizontalPadding.dp
    }
    val buttonSpacing = if (actionStrip) {
        tuning.bottomActionButtonSpacing.dp
    } else if (compact) {
        tuning.bottomTabletCompactActionButtonSpacing.dp
    } else {
        tuning.bottomTabletActionButtonSpacing.dp
    }
    val iconSpacing = if (actionStrip) {
        tuning.bottomActionIconSpacing.dp
    } else if (compact) {
        tuning.bottomTabletCompactActionIconSpacing.dp
    } else {
        tuning.bottomTabletActionIconSpacing.dp
    }
    val iconSize = if (actionStrip) {
        tuning.bottomActionIconSize.dp
    } else {
        tuning.bottomTabletActionIconSize.dp
    }
    val actionTextStyle = if (actionStrip) {
        MaterialTheme.typography.labelMedium.copy(fontSize = tuning.bottomActionTextSize.sp)
    } else {
        MaterialTheme.typography.labelLarge
    }
    val buttonContentPadding = PaddingValues(
        horizontal = if (actionStrip) {
            tuning.bottomActionButtonContentHorizontalPadding.dp
        } else if (compact) {
            tuning.bottomTabletCompactActionButtonContentHorizontalPadding.dp
        } else {
            tuning.bottomTabletActionButtonContentHorizontalPadding.dp
        },
    )
    val containerHeight = if (actionStrip) tuning.bottomEditActionStripHeight.dp else tuning.bottomBarHeight.dp
    val buttonHeight = if (actionStrip) {
        tuning.bottomEditActionStripButtonHeight.dp
    } else {
        tuning.bottomTabletActionButtonHeight.dp
    }
    val topPadding = if (actionStrip) {
        tuning.bottomActionTopPadding.dp
    } else {
        tuning.bottomTabletActionTopPadding.dp
    }
    val bottomPadding = if (actionStrip) {
        tuning.bottomActionBottomPadding.dp
    } else {
        tuning.bottomTabletActionBottomPadding.dp
    }

    Row(
        modifier = modifier
            .height(containerHeight)
            .padding(
                start = horizontalPadding,
                top = topPadding,
                end = horizontalPadding,
                bottom = bottomPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saveHasPendingChanges) {
            Button(
                onClick = { actions.onSave.invoke() },
                enabled = saveEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = neutralButtonContainer,
                    disabledContentColor = disabledButtonContent,
                ),
                contentPadding = buttonContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(modifier = Modifier.width(iconSpacing))
                Text(
                    text = stringResource(R.string.edit_metadata_save),
                    style = actionTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            OutlinedButton(
                onClick = { actions?.onSave?.invoke() },
                enabled = saveEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = neutralButtonContainer,
                    contentColor = neutralButtonContent,
                    disabledContainerColor = neutralButtonContainer,
                    disabledContentColor = disabledButtonContent,
                ),
                border = BorderStroke(1.dp, neutralButtonBorder),
                contentPadding = buttonContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(modifier = Modifier.width(iconSpacing))
                Text(
                    text = stringResource(R.string.edit_metadata_save),
                    style = actionTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = { actions?.onShareExport?.invoke() },
            enabled = actionsEnabled,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = neutralButtonContainer,
                contentColor = neutralButtonContent,
                disabledContainerColor = neutralButtonContainer,
                disabledContentColor = disabledButtonContent,
            ),
            border = BorderStroke(1.dp, neutralButtonBorder),
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(iconSpacing))
            Text(
                text = exportLabel,
                style = actionTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = { actions?.onSendToKindle?.invoke() },
            enabled = actionsEnabled,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = neutralButtonContainer,
                contentColor = neutralButtonContent,
                disabledContainerColor = neutralButtonContainer,
                disabledContentColor = disabledButtonContent,
            ),
            border = BorderStroke(1.dp, neutralButtonBorder),
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            Spacer(modifier = Modifier.width(iconSpacing))
            Text(
                text = stringResource(R.string.edit_metadata_kindle_short),
                style = actionTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolioreBottomNavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean = true,
    itemHeight: Dp,
    iconSize: Dp,
    labelSpacing: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }.copy(alpha = if (enabled) 1f else 0.42f)

    Column(
        modifier = modifier
            .height(itemHeight)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = itemColor,
        )
        Spacer(modifier = Modifier.height(labelSpacing))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = itemColor,
            maxLines = 1,
        )
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.returnToLibrary() {
    if (!popBackStack(Routes.LIBRARY, inclusive = false)) {
        navigateTopLevel(Routes.LIBRARY)
    }
}

private enum class BottomBarMode {
    TopLevel,
    Edit,
    EditStacked,
}

private enum class TopChromeMode {
    None,
    Library,
    Edit,
}

private data class EditBottomBarState(
    val bookId: String,
    val actions: EditBottomBarActions,
)

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ARG_BOOK_ID = "bookId"
    const val DETAILS = "details/{$ARG_BOOK_ID}"
    const val EDIT = "edit/{$ARG_BOOK_ID}"

    fun details(bookId: String): String = "details/$bookId"
    fun edit(bookId: String): String = "edit/$bookId"
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.routeEnterTransition(): EnterTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val fadeSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

    return when {
        from == Routes.LIBRARY && to == Routes.EDIT -> {
            slideInHorizontally(
                animationSpec = tween(durationMillis = RouteTransitionMillis, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth },
            ) + fadeIn(animationSpec = fadeSpec)
        }
        from == Routes.EDIT && to == Routes.LIBRARY -> {
            slideInHorizontally(
                animationSpec = tween(durationMillis = RouteTransitionMillis, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> -fullWidth },
            ) + fadeIn(animationSpec = fadeSpec)
        }
        else -> EnterTransition.None
    }
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.routeExitTransition(): ExitTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val fadeSpec = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)

    return when {
        from == Routes.LIBRARY && to == Routes.EDIT -> {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = RouteTransitionMillis, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> -fullWidth },
            ) + fadeOut(animationSpec = fadeSpec)
        }
        from == Routes.EDIT && to == Routes.LIBRARY -> {
            slideOutHorizontally(
                animationSpec = tween(durationMillis = RouteTransitionMillis, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> fullWidth },
            ) + fadeOut(animationSpec = fadeSpec)
        }
        else -> ExitTransition.None
    }
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    companion object {
        val items = listOf(
            TopLevelDestination(
                route = Routes.LIBRARY,
                labelRes = R.string.nav_library,
                icon = Icons.AutoMirrored.Outlined.MenuBook,
            ),
            TopLevelDestination(
                route = Routes.SETTINGS,
                labelRes = R.string.nav_settings,
                icon = Icons.Outlined.Settings,
            ),
        )
        val routes = items.map { it.route }.toSet()
    }
}
