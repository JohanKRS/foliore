package com.foliore.android.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showTopLevelNavigation = currentRoute in TopLevelDestination.routes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp

        if (expanded && showTopLevelNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                FolioreNavigationRail(
                    selectedRoute = currentRoute,
                    onDestinationClick = { route ->
                        navController.navigateTopLevel(route)
                    },
                )
                NavContent(
                    modifier = Modifier.weight(1f),
                    navController = navController,
                    libraryViewModel = libraryViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onBookClick = { bookId -> navController.navigate(Routes.details(bookId)) },
                    onEditBook = { bookId -> navController.navigate(Routes.edit(bookId)) },
                    onBack = { navController.popBackStack() },
                )
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showTopLevelNavigation) {
                        FolioreNavigationBar(
                            selectedRoute = currentRoute,
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
                    onBookClick = { bookId -> navController.navigate(Routes.details(bookId)) },
                    onEditBook = { bookId -> navController.navigate(Routes.edit(bookId)) },
                    onBack = { navController.popBackStack() },
                )
            }
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
    onBookClick: (String) -> Unit,
    onEditBook: (String) -> Unit,
    onBack: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(Routes.LIBRARY) {
            LibraryRoute(
                viewModel = libraryViewModel,
                onBookClick = onBookClick,
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
            )
        }
    }
}

@Composable
private fun FolioreNavigationBar(
    selectedRoute: String?,
    onDestinationClick: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        TopLevelDestination.items.forEach { destination ->
            val selected = selectedRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationClick(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun FolioreNavigationRail(
    selectedRoute: String?,
    onDestinationClick: (String) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        TopLevelDestination.items.forEach { destination ->
            val selected = selectedRoute == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { onDestinationClick(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
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
