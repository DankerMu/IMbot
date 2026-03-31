@file:Suppress("FunctionName")

package com.imbot.android.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imbot.android.data.RelaySettings
import com.imbot.android.ui.components.ConnectionBanner
import com.imbot.android.ui.detail.DetailViewModel
import com.imbot.android.ui.detail.SessionDetailScreen
import com.imbot.android.ui.home.HomeScreen
import com.imbot.android.ui.home.HomeViewModel
import com.imbot.android.ui.newsession.NewSessionScreen
import com.imbot.android.ui.newsession.NewSessionViewModel
import com.imbot.android.ui.onboarding.OnboardingScreen
import com.imbot.android.ui.onboarding.OnboardingViewModel
import com.imbot.android.ui.prototype.PrototypeScreen
import com.imbot.android.ui.settings.SettingsScreen
import com.imbot.android.ui.settings.SettingsViewModel
import com.imbot.android.ui.theme.imbotEnterTransition
import com.imbot.android.ui.theme.imbotExitTransition
import com.imbot.android.ui.theme.imbotPopEnterTransition
import com.imbot.android.ui.theme.imbotPopExitTransition
import com.imbot.android.ui.workspace.RootDetailScreen
import com.imbot.android.ui.workspace.RootDetailViewModel
import com.imbot.android.ui.workspace.WorkspaceScreen
import com.imbot.android.ui.workspace.WorkspaceViewModel
import com.imbot.android.viewmodel.MainNavigationEvent
import com.imbot.android.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    mainViewModel: MainViewModel,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by mainViewModel.connectionState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map(TopLevelDestination::route)

    LaunchedEffect(startDestination) {
        if (startDestination != AppRoute.ONBOARDING) {
            mainViewModel.connectConfiguredRelayIfNeeded()
            homeViewModel.refresh()
        }
    }

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
                is MainNavigationEvent.OpenSessionDetail ->
                    navController.navigate(AppRoute.sessionDetail(event.sessionId)) {
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            ConnectionBanner(
                connectionState = connectionState,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
            ) {
                composable(
                    route = AppRoute.ONBOARDING,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    val viewModel: OnboardingViewModel = hiltViewModel()
                    OnboardingScreen(
                        viewModel = viewModel,
                        onNavigateHome = {
                            mainViewModel.connectConfiguredRelayIfNeeded()
                            homeViewModel.refresh()
                            navigateAfterOnboarding(navController)
                        },
                    )
                }

                composable(
                    route = AppRoute.HOME,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onCreateSession = mainViewModel::openNewSession,
                        onOpenSession = mainViewModel::openSession,
                    )
                }

                composable(
                    route = AppRoute.WORKSPACE,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    val viewModel: WorkspaceViewModel = hiltViewModel()
                    WorkspaceScreen(
                        viewModel = viewModel,
                        onOpenRoot = { root ->
                            navController.navigate(
                                AppRoute.rootDetail(
                                    rootId = root.id,
                                    hostId = root.hostId,
                                    path = root.path,
                                ),
                            )
                        },
                    )
                }

                composable(
                    route = AppRoute.SETTINGS,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsScreen(viewModel = viewModel)
                }

                composable(
                    route = AppRoute.PROTOTYPE,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    PrototypeScreen(
                        viewModel = mainViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                    )
                }

                composable(
                    route = AppRoute.NEW_SESSION,
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    val viewModel: NewSessionViewModel = hiltViewModel()
                    NewSessionScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onSessionCreated = { sessionId ->
                            homeViewModel.refresh()
                            mainViewModel.openSession(sessionId)
                        },
                    )
                }

                composable(
                    route = AppRoute.SESSION_DETAIL,
                    arguments =
                        listOf(
                            navArgument(AppRoute.SESSION_ID_ARG) {
                                type = NavType.StringType
                            },
                        ),
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) { backStackEntry ->
                    val viewModel: DetailViewModel = hiltViewModel()
                    val sessionId = backStackEntry.arguments?.getString(AppRoute.SESSION_ID_ARG).orEmpty()
                    SessionDetailScreen(
                        viewModel = viewModel,
                        sessionId = sessionId,
                        onNavigateBack = { refreshHome ->
                            if (refreshHome) {
                                homeViewModel.refresh()
                            }
                            navController.popBackStack()
                        },
                    )
                }

                composable(
                    route = AppRoute.ROOT_DETAIL,
                    arguments =
                        listOf(
                            navArgument(RootDetailViewModel.ROOT_ID_ARG) { type = NavType.StringType },
                            navArgument(RootDetailViewModel.HOST_ID_ARG) { type = NavType.StringType },
                            navArgument(RootDetailViewModel.PATH_ARG) { type = NavType.StringType },
                        ),
                    enterTransition = { imbotEnterTransition() },
                    exitTransition = { imbotExitTransition() },
                    popEnterTransition = { imbotPopEnterTransition() },
                    popExitTransition = { imbotPopExitTransition() },
                    sizeTransform = { null },
                ) {
                    val viewModel: RootDetailViewModel = hiltViewModel()
                    RootDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onOpenSession = { sessionId ->
                            mainViewModel.openSession(sessionId)
                        },
                    )
                }
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

private fun navigateAfterOnboarding(navController: androidx.navigation.NavHostController) {
    val spec = onboardingCompletionNavigation()
    navController.navigate(spec.route) {
        popUpTo(spec.popUpTo) {
            inclusive = spec.inclusive
        }
        launchSingleTop = true
    }
}

internal fun resolveStartDestination(settings: RelaySettings): String =
    if (settings.relayUrl.isBlank() || settings.token.isBlank()) {
        AppRoute.ONBOARDING
    } else {
        AppRoute.HOME
    }

internal data class OnboardingCompletionNavigation(
    val route: String,
    val popUpTo: String,
    val inclusive: Boolean,
)

internal fun onboardingCompletionNavigation(): OnboardingCompletionNavigation =
    OnboardingCompletionNavigation(
        route = AppRoute.HOME,
        popUpTo = AppRoute.ONBOARDING,
        inclusive = true,
    )

internal object AppRoute {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val WORKSPACE = "workspace"
    const val SETTINGS = "settings"
    const val PROTOTYPE = "prototype"
    const val NEW_SESSION = "new_session"
    const val SESSION_ID_ARG = "sessionId"
    const val SESSION_DETAIL = "session/{$SESSION_ID_ARG}"
    const val ROOT_DETAIL =
        "workspace/root/{${RootDetailViewModel.ROOT_ID_ARG}}" +
            "?${RootDetailViewModel.HOST_ID_ARG}={${RootDetailViewModel.HOST_ID_ARG}}" +
            "&${RootDetailViewModel.PATH_ARG}={${RootDetailViewModel.PATH_ARG}}"

    fun sessionDetail(sessionId: String): String = "session/$sessionId"

    fun rootDetail(
        rootId: String,
        hostId: String,
        path: String,
    ): String =
        "workspace/root/${Uri.encode(rootId)}" +
            "?${RootDetailViewModel.HOST_ID_ARG}=${Uri.encode(hostId)}" +
            "&${RootDetailViewModel.PATH_ARG}=${Uri.encode(path)}"
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
