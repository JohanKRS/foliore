package com.foliore.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.foliore.android.ui.details.EditMetadataRoute
import com.foliore.android.ui.library.LibraryRoute
import com.foliore.android.ui.library.SettingsRoute
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.LibraryViewModel

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
    val showTopLevelNavigation = currentRoute in TopLevelDestination.routes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTopLevelNavigation) {
                FolioreNavigationBar(
                    selectedRoute = currentRoute,
                    isSyncing = libraryState.isSyncing,
                    onSyncClick = libraryViewModel::syncNow,
                    onDestinationClick = { route ->
                        navController.navigateTopLevel(route)
                    },
                )
            }
        },
    ) { paddingValues ->
        NavContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showTopLevelNavigation) paddingValues else PaddingValues(0.dp)),
            navController = navController,
            libraryViewModel = libraryViewModel,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBookClick = { bookId ->
                navController.navigate(Routes.details(bookId))
            },
            onEditBook = { bookId -> navController.navigate(Routes.edit(bookId)) },
            onBack = { navController.popBackStack() },
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
    onBookClick: (String) -> Unit,
    onEditBook: (String) -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val openTabletWorkspace = maxWidth >= 840.dp

        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.fillMaxSize(),
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
                    onLibraryClick = {
                        navController.navigateTopLevel(Routes.LIBRARY)
                    },
                    onSettingsClick = {
                        navController.navigateTopLevel(Routes.SETTINGS)
                    },
                )
            }
        }
    }
}

@Composable
private fun FolioreNavigationBar(
    selectedRoute: String?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onDestinationClick: (String) -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor),
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val libraryDestination = TopLevelDestination.items.first()
            val settingsDestination = TopLevelDestination.items.last()
            FolioreBottomNavigationItem(
                icon = libraryDestination.icon,
                label = stringResource(libraryDestination.labelRes),
                contentDescription = stringResource(libraryDestination.labelRes),
                selected = selectedRoute == libraryDestination.route,
                onClick = { onDestinationClick(libraryDestination.route) },
                modifier = Modifier.weight(1f),
            )
            FolioreBottomNavigationItem(
                icon = Icons.Outlined.Sync,
                label = stringResource(R.string.nav_sync),
                contentDescription = stringResource(R.string.action_sync_now),
                selected = false,
                enabled = !isSyncing,
                onClick = onSyncClick,
                modifier = Modifier.weight(1f),
            )
            FolioreBottomNavigationItem(
                icon = settingsDestination.icon,
                label = stringResource(settingsDestination.labelRes),
                contentDescription = stringResource(settingsDestination.labelRes),
                selected = selectedRoute == settingsDestination.route,
                onClick = { onDestinationClick(settingsDestination.route) },
                modifier = Modifier.weight(1f),
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
            .height(72.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = itemColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = itemColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .background(if (selected) itemColor else Color.Transparent),
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
