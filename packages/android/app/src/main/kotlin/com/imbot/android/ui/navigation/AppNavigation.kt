@file:Suppress("FunctionName")

package com.imbot.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.imbot.android.ui.home.HomeScreen
import com.imbot.android.ui.home.HomeViewModel
import com.imbot.android.ui.newsession.NewSessionScreen
import com.imbot.android.ui.newsession.NewSessionViewModel
import com.imbot.android.ui.prototype.PrototypeScreen
import com.imbot.android.ui.settings.SettingsScreen
import com.imbot.android.ui.workspace.WorkspaceScreen
import com.imbot.android.viewmodel.MainNavigationEvent
import com.imbot.android.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map(TopLevelDestination::route)

    BackHandler(enabled = currentRoute != AppRoute.HOME && showBottomBar) {
        navigateToTopLevel(navController, AppRoute.HOME)
    }

    LaunchedEffect(mainViewModel, navController) {
        mainViewModel.navigationEvents.collectLatest { event ->
            when (event) {
                MainNavigationEvent.OpenHome -> navigateToTopLevel(navController, AppRoute.HOME)
                MainNavigationEvent.OpenNewSession ->
                    navController.navigate(AppRoute.NEW_SESSION) {
                        launchSingleTop = true
                    }
                MainNavigationEvent.OpenPrototype ->
                    navController.navigate(AppRoute.PROTOTYPE) {
                        launchSingleTop = true
                    }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { navDestination ->
                                navDestination.route == destination.route
                            } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navigateToTopLevel(navController, destination.route)
                            },
                            icon = {
                                val iconContent: @Composable () -> Unit = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                }

                                if (
                                    destination.route == AppRoute.HOME &&
                                    homeUiState.runningSessionCount > 0 &&
                                    currentRoute != AppRoute.HOME
                                ) {
                                    BadgedBox(
                                        badge = {
                                            Badge()
                                        },
                                    ) {
                                        iconContent()
                                    }
                                } else {
                                    iconContent()
                                }
                            },
                            label = {
                                Text(destination.label)
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onCreateSession = mainViewModel::openNewSession,
                    onOpenSession = mainViewModel::openSession,
                )
            }
            composable(AppRoute.WORKSPACE) {
                WorkspaceScreen()
            }
            composable(AppRoute.SETTINGS) {
                SettingsScreen(
                    viewModel = mainViewModel,
                    onOpenPrototype = mainViewModel::openPrototypeComposer,
                )
            }
            composable(AppRoute.PROTOTYPE) {
                PrototypeScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                )
            }
            composable(AppRoute.NEW_SESSION) {
                val viewModel: NewSessionViewModel = hiltViewModel()
                NewSessionScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSessionCreated = {
                        homeViewModel.refresh()
                        navigateToTopLevel(navController, AppRoute.HOME)
                    },
                )
            }
        }
    }
}

private fun navigateToTopLevel(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private object AppRoute {
    const val HOME = "home"
    const val WORKSPACE = "workspace"
    const val SETTINGS = "settings"
    const val PROTOTYPE = "prototype"
    const val NEW_SESSION = "new_session"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations =
    listOf(
        TopLevelDestination(
            route = AppRoute.HOME,
            label = "会话",
            icon = Icons.Filled.Home,
        ),
        TopLevelDestination(
            route = AppRoute.WORKSPACE,
            label = "目录",
            icon = Icons.Filled.Folder,
        ),
        TopLevelDestination(
            route = AppRoute.SETTINGS,
            label = "设置",
            icon = Icons.Filled.Settings,
        ),
    )
