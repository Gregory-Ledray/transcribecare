package com.transcribecare.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.transcribecare.app.service.ShareService
import com.transcribecare.app.ui.screens.HistoryScreen
import com.transcribecare.app.ui.screens.HomeScreen
import com.transcribecare.app.ui.screens.SessionDetailScreen
import com.transcribecare.app.ui.screens.SettingsScreen
import com.transcribecare.app.viewmodel.HistoryViewModel
import com.transcribecare.app.viewmodel.HomeViewModel
import com.transcribecare.app.viewmodel.SettingsViewModel

/**
 * Navigation route constants for the app.
 */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SESSION_DETAIL = "session_detail/{sessionId}"

    fun sessionDetail(sessionId: String) = "session_detail/$sessionId"
}

/**
 * Sealed class representing the bottom navigation tabs.
 */
sealed class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : BottomNavTab(Routes.HOME, "Home", Icons.Filled.Home)
    data object History : BottomNavTab(Routes.HISTORY, "History", Icons.AutoMirrored.Filled.List)
    data object Settings : BottomNavTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
}

private val bottomNavTabs = listOf(BottomNavTab.Home, BottomNavTab.History, BottomNavTab.Settings)

/**
 * Root navigation composable for the TranscribeCare app.
 *
 * Sets up Navigation Compose with three main destinations (Home, History, Settings)
 * and a session detail screen. Displays a bottom navigation bar with active tab
 * highlighting using the primary color. Preserves state across tab switches using
 * saveState/restoreState in navigate().
 *
 * ViewModels are created at the navigation level so they survive tab switches.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    // ViewModels scoped at the navigation level to survive tab switches
    val homeViewModel: HomeViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // Share service instance
    val shareService = ShareService()

    // Determine if bottom bar should be visible (hide on detail screens)
    val showBottomBar = currentDestination?.route?.let { route ->
        bottomNavTabs.any { it.route == route }
    } ?: true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == tab.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Pop up to the start destination to avoid building up
                                    // a large stack of destinations on the back stack
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when re-selecting a previously selected tab
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onSessionClick = { sessionId ->
                        navController.navigate(Routes.sessionDetail(sessionId))
                    },
                    onShareClick = { session ->
                        val intent = shareService.createShareIntent(session, context)
                        val chooser = Intent.createChooser(intent, "Share Session")
                        context.startActivity(chooser)
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }

            composable(
                route = Routes.SESSION_DETAIL,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                // Find the session from the history view model's session list
                val sessions by historyViewModel.sessions.collectAsStateWithLifecycle()
                val session = sessions.find { it.id == sessionId }

                session?.let {
                    SessionDetailScreen(
                        session = it,
                        settingsViewModel = settingsViewModel,
                    ) {
                        val intent = shareService.createShareIntent(it, context)
                        val chooser = Intent.createChooser(intent, "Share Session")
                        context.startActivity(chooser)
                    }
                }
            }
        }
    }
}
