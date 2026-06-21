package com.foliore.android.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.foliore.android.R
import com.foliore.android.ui.details.BookDetailsRoute
import com.foliore.android.ui.details.EditBottomBarActions
import com.foliore.android.ui.details.EditMetadataRoute
import com.foliore.android.ui.details.TabletEditLeftPaneWeight
import com.foliore.android.ui.details.TabletEditRightPaneWeight
import com.foliore.android.ui.library.EditorialDivider
import com.foliore.android.ui.library.LibraryRoute
import com.foliore.android.ui.library.SettingsRoute
import com.foliore.android.ui.library.SyncProgressDivider
import com.foliore.android.ui.library.SyncProgressStatusLine
import com.foliore.android.ui.theme.ThemeMode
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
private val BottomNavigationMaxWidth = 560.dp
private val BottomNavigationHorizontalPadding = 28.dp
private val BottomNavigationTopLevelSpacing = 18.dp
private val BottomNavigationEditSpacing = 10.dp
@Composable
fun FolioreNavHost(
    libraryViewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val currentBookId = backStackEntry?.arguments?.getString(Routes.ARG_BOOK_ID)
    var editBottomBarState by remember { mutableStateOf<EditBottomBarState?>(null) }
    val chromeHazeState = rememberHazeState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val openTabletWorkspace = maxWidth >= 840.dp
        val showTopLevelNavigation = currentRoute in TopLevelDestination.routes
        val showEditActions = currentRoute == Routes.EDIT
        val showEditNavigation = showEditActions && openTabletWorkspace
        val showNavigation = showTopLevelNavigation || showEditActions
        val editActions = editBottomBarState
            ?.takeIf { showEditActions && it.bookId == currentBookId }
            ?.actions

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                        hazeState = chromeHazeState,
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
                currentRoute == Routes.LIBRARY -> PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = 0.dp,
                )
                else -> paddingValues
            }

            NavContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                navController = navController,
                libraryViewModel = libraryViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
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
                chromeHazeState = chromeHazeState,
                bottomChromeInset = if (showNavigation && currentRoute == Routes.LIBRARY) {
                    BottomBarContentInset
                } else {
                    0.dp
                },
            )
        }
    }
}

@Composable
private fun NavContent(
    modifier: Modifier,
    navController: NavHostController,
    libraryViewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEditBook: (String) -> Unit,
    onBack: () -> Unit,
    onEditBottomBarActionsChange: (EditBottomBarActions?) -> Unit,
    chromeHazeState: HazeState,
    bottomChromeInset: androidx.compose.ui.unit.Dp,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.LIBRARY) {
            LibraryRoute(
                viewModel = libraryViewModel,
                onBookClick = { bookId -> navController.navigate(Routes.edit(bookId)) },
                onSettingsClick = { navController.navigateTopLevel(Routes.SETTINGS) },
                chromeHazeState = chromeHazeState,
                bottomChromeInset = bottomChromeInset,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                viewModel = libraryViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        composable(Routes.DETAILS) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            BookDetailsRoute(
                viewModel = libraryViewModel,
                bookId = bookId,
                onEditMetadata = { onEditBook(bookId) },
                onBack = onBack,
            )
        }
        composable(Routes.EDIT) { backStackEntry ->
            EditMetadataRoute(
                viewModel = libraryViewModel,
                bookId = backStackEntry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty(),
                onBack = onBack,
                onBottomBarActionsChange = onEditBottomBarActionsChange,
            )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
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
        if (isLoading) {
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
        SyncProgressDivider(
            isActive = isLoading,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )

        if (stackedEditMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomEditActionStripHeight + BottomBarHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                EditActionsSlot(
                    editActions = editActions,
                    isLoading = isLoading,
                    compact = true,
                    delayMillis = 90,
                    actionStrip = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BottomEditActionStripHeight),
                )
                AnimatedNavigationItemsSlot(
                    selectedRoute = navigationSelectedRoute,
                    splitEditMode = false,
                    splitNavigationWidth = BottomNavigationMaxWidth,
                    isLoading = isLoading,
                    onLibraryClick = onLibraryClick,
                    onSyncClick = onSyncClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BottomBarHeight),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomBarHeight)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BottomBarHeight),
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

    BoxWithConstraints(modifier = modifier) {
        val topRowWidth = if (maxWidth < BottomNavigationMaxWidth) {
            maxWidth
        } else {
            BottomNavigationMaxWidth
        }
        val splitRowWidth = if (splitNavigationWidth < BottomNavigationMaxWidth) {
            splitNavigationWidth
        } else {
            BottomNavigationMaxWidth
        }
        val topRowStart = (maxWidth - topRowWidth) / 2f
        val splitRowStart = (splitNavigationWidth - splitRowWidth) / 2f
        val topContentWidth = topRowWidth - (BottomNavigationHorizontalPadding * 2f)
        val topItemWidth = (topContentWidth - (BottomNavigationTopLevelSpacing * 2f)) / 3f
        val splitContentWidth = splitRowWidth - (BottomNavigationHorizontalPadding * 2f)
        val splitItemWidth = (splitContentWidth - (BottomNavigationEditSpacing * 2f)) / 3f

        AnimatedNavigationItem(
            index = 0,
            transition = transition,
            topStart = topRowStart + BottomNavigationHorizontalPadding,
            splitStart = splitRowStart + BottomNavigationHorizontalPadding,
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = libraryDestination.icon,
            label = stringResource(libraryDestination.labelRes),
            contentDescription = stringResource(libraryDestination.labelRes),
            selected = selectedRoute == libraryDestination.route || splitEditMode,
            onClick = onLibraryClick,
        )
        AnimatedNavigationItem(
            index = 1,
            transition = transition,
            topStart = topRowStart + BottomNavigationHorizontalPadding + topItemWidth + BottomNavigationTopLevelSpacing,
            splitStart = splitRowStart + BottomNavigationHorizontalPadding + splitItemWidth + BottomNavigationEditSpacing,
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = Icons.Outlined.Sync,
            label = stringResource(R.string.nav_sync),
            contentDescription = stringResource(R.string.action_sync_now),
            selected = false,
            enabled = !isLoading,
            onClick = onSyncClick,
        )
        AnimatedNavigationItem(
            index = 2,
            transition = transition,
            topStart = topRowStart + BottomNavigationHorizontalPadding +
                ((topItemWidth + BottomNavigationTopLevelSpacing) * 2f),
            splitStart = splitRowStart + BottomNavigationHorizontalPadding +
                ((splitItemWidth + BottomNavigationEditSpacing) * 2f),
            topWidth = topItemWidth,
            splitWidth = splitItemWidth,
            icon = settingsDestination.icon,
            label = stringResource(settingsDestination.labelRes),
            contentDescription = stringResource(settingsDestination.labelRes),
            selected = selectedRoute == settingsDestination.route,
            onClick = onSettingsClick,
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
            .offset(x = x, y = BottomBarItemTopPadding)
            .width(width),
    )
}

@Composable
private fun EditActionsSlot(
    editActions: EditBottomBarActions?,
    isLoading: Boolean,
    compact: Boolean,
    delayMillis: Int,
    actionStrip: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        androidx.compose.animation.AnimatedVisibility(
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
    val horizontalPadding = if (compact) 12.dp else 24.dp
    val buttonSpacing = if (compact) 8.dp else 16.dp
    val iconSpacing = if (compact) 6.dp else 9.dp
    val buttonContentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp)
    val containerHeight = if (actionStrip) BottomEditActionStripHeight else BottomBarHeight
    val buttonHeight = if (actionStrip) BottomEditActionStripButtonHeight else BottomBarItemHeight
    val topPadding = if (actionStrip) 6.dp else BottomBarItemTopPadding
    val bottomPadding = if (actionStrip) 8.dp else BottomBarItemBottomPadding

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
        OutlinedButton(
            onClick = { actions?.onSave?.invoke() },
            enabled = saveEnabled,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(iconSpacing))
            Text(
                text = stringResource(R.string.edit_metadata_save),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = { actions?.onShareExport?.invoke() },
            enabled = actionsEnabled,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(iconSpacing))
            Text(
                text = exportLabel,
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
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
            contentPadding = buttonContentPadding,
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(iconSpacing))
            Text(
                text = stringResource(R.string.edit_metadata_kindle_short),
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
            .height(BottomBarItemHeight)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = itemColor,
        )
        Spacer(modifier = Modifier.height(3.dp))
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
