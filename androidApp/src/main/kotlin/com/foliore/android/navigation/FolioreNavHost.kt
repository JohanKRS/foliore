package com.foliore.android.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
        val showEditNavigation = currentRoute == Routes.EDIT && openTabletWorkspace
        val showNavigation = showTopLevelNavigation || showEditNavigation
        val editActions = editBottomBarState
            ?.takeIf { showEditNavigation && it.bookId == currentBookId }
            ?.actions

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNavigation) {
                    FolioreNavigationBar(
                        selectedRoute = currentRoute,
                        mode = if (showEditNavigation) {
                            BottomBarMode.Edit
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
                openTabletWorkspace = openTabletWorkspace,
                libraryViewModel = libraryViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBookClick = { bookId ->
                    navController.navigate(Routes.details(bookId))
                },
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
    openTabletWorkspace: Boolean,
    libraryViewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBookClick: (String) -> Unit,
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
                onBookClick = { bookId ->
                    if (openTabletWorkspace) {
                        navController.navigate(Routes.edit(bookId))
                    } else {
                        onBookClick(bookId)
                    }
                },
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
    val editMode = mode == BottomBarMode.Edit
    val navWeight by animateFloatAsState(
        targetValue = if (editMode) 0.46f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "bottomBarNavWeight",
    )
    val actionWeight by animateFloatAsState(
        targetValue = if (editMode) 0.54f else 0.001f,
        animationSpec = tween(durationMillis = 260),
        label = "bottomBarActionWeight",
    )
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
            SyncProgressStatusLine(
                progress = progress,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 8.dp, end = 28.dp, bottom = 6.dp),
            )
        }
        SyncProgressDivider(
            isActive = isLoading,
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarHeight),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(BottomBarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(navWeight)
                        .fillMaxHeight(),
                    contentAlignment = if (editMode) Alignment.Center else Alignment.TopCenter,
                ) {
                    FolioreBottomNavigationItems(
                        selectedRoute = selectedRoute,
                        editMode = editMode,
                        isLoading = isLoading,
                        onLibraryClick = onLibraryClick,
                        onSyncClick = onSyncClick,
                        onSettingsClick = onSettingsClick,
                        modifier = Modifier
                            .widthIn(max = if (editMode) 760.dp else 560.dp)
                            .fillMaxWidth()
                            .padding(top = if (editMode) 0.dp else 8.dp)
                            .padding(horizontal = if (editMode) 42.dp else 28.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(
                            if (editMode) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
                            } else {
                                Color.Transparent
                            },
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(actionWeight)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = editMode) {
                        FolioreEditActions(
                            actions = editActions,
                            isLoading = isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolioreBottomNavigationItems(
    selectedRoute: String?,
    editMode: Boolean,
    isLoading: Boolean,
    onLibraryClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryDestination = TopLevelDestination.items.first()
    val settingsDestination = TopLevelDestination.items.last()

    Row(
        modifier = modifier,
        horizontalArrangement = if (editMode) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(18.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolioreBottomNavigationItem(
            icon = libraryDestination.icon,
            label = stringResource(libraryDestination.labelRes),
            contentDescription = stringResource(libraryDestination.labelRes),
            selected = selectedRoute == libraryDestination.route || editMode,
            onClick = onLibraryClick,
            modifier = if (editMode) {
                Modifier.width(72.dp)
            } else {
                Modifier.weight(1f)
            },
        )
        FolioreBottomNavigationItem(
            icon = Icons.Outlined.Sync,
            label = stringResource(R.string.nav_sync),
            contentDescription = stringResource(R.string.action_sync_now),
            selected = false,
            enabled = !isLoading,
            onClick = onSyncClick,
            modifier = if (editMode) {
                Modifier.width(72.dp)
            } else {
                Modifier.weight(1f)
            },
        )
        FolioreBottomNavigationItem(
            icon = settingsDestination.icon,
            label = stringResource(settingsDestination.labelRes),
            contentDescription = stringResource(settingsDestination.labelRes),
            selected = selectedRoute == settingsDestination.route,
            onClick = onSettingsClick,
            modifier = if (editMode) {
                Modifier.width(72.dp)
            } else {
                Modifier.weight(1f)
            },
        )
    }
}

@Composable
private fun FolioreEditActions(
    actions: EditBottomBarActions?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val exportLabel = stringResource(R.string.edit_metadata_export_short)
    val actionsEnabled = actions != null && !isLoading
    val saveEnabled = actions?.saveEnabled == true && !isLoading

    Row(
        modifier = modifier
            .height(BottomBarHeight)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { actions?.onSave?.invoke() },
            enabled = saveEnabled,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
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
        Button(
            onClick = { actions?.onShareExport?.invoke() },
            enabled = actionsEnabled,
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
        OutlinedButton(
            onClick = { actions?.onSendToKindle?.invoke() },
            enabled = actionsEnabled,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
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
            .height(56.dp)
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
