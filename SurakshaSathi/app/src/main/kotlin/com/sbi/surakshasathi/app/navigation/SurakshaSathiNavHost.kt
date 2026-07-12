package com.sbi.surakshasathi.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.sbi.surakshasathi.app.presentation.HomeHubScreen
import com.sbi.surakshasathi.app.presentation.OnboardingScreen
import com.sbi.surakshasathi.app.presentation.PermissionsScreen
import com.sbi.surakshasathi.feature.adaptivefriction.presentation.ConfirmTransferScreen
import com.sbi.surakshasathi.feature.adaptivefriction.presentation.LivenessCheckScreen
import com.sbi.surakshasathi.feature.apkscan.presentation.ApkAlertScreen
import com.sbi.surakshasathi.feature.apkscan.presentation.ApkScanScreen
import com.sbi.surakshasathi.feature.awareness.presentation.AwarenessScreen
import com.sbi.surakshasathi.feature.awareness.presentation.BadgesScreen
import com.sbi.surakshasathi.feature.awareness.presentation.LessonDetailScreen
import com.sbi.surakshasathi.feature.awareness.presentation.OfficialLinkScannerScreen
import com.sbi.surakshasathi.feature.frauddashboard.presentation.FraudDashboardScreen
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageDetailScreen
import com.sbi.surakshasathi.feature.messagescan.presentation.MessageScanScreen
import com.sbi.surakshasathi.feature.ncrpreport.presentation.NcrpReportScreen
import com.sbi.surakshasathi.feature.ragwarning.presentation.RagWarningScreen

// ── Bottom Nav Items ──────────────────────────────────────────────────────────
private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
)

@Composable
private fun bottomNavItems() =
    listOf(
        BottomNavItem(
            screen = Screen.Home,
            label = "Home",
            selectedIcon = Icons.Filled.Shield,
            unselectedIcon = Icons.Outlined.Shield,
            contentDescription = "Home — protection status",
        ),
        BottomNavItem(
            screen = Screen.Alerts,
            label = "Alerts",
            selectedIcon = Icons.Filled.NotificationsActive,
            unselectedIcon = Icons.Outlined.Notifications,
            contentDescription = "Alerts — flagged messages and APKs",
        ),
        BottomNavItem(
            screen = Screen.Dashboard,
            label = "Dashboard",
            selectedIcon = Icons.Filled.Map,
            unselectedIcon = Icons.Outlined.Map,
            contentDescription = "Dashboard — fraud heatmap",
        ),
        BottomNavItem(
            screen = Screen.Learn,
            label = "Learn",
            selectedIcon = Icons.Filled.School,
            unselectedIcon = Icons.Outlined.School,
            contentDescription = "Learn — cyber-safety lessons",
        ),
    )

/**
 * Root navigation host for SurakshaSathi.
 *
 * Structure:
 * - Onboarding flow (splash → onboarding → permissions) — no bottom nav
 * - Main app with 4-tab bottom navigation (Home | Alerts | Dashboard | Learn)
 * - Feature screens launched on top of the main shell
 */
@Composable
fun SurakshaSathiNavHost(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomNavItems = bottomNavItems()

    // Routes that show the bottom nav bar
    val bottomNavRoutes =
        setOf(
            Screen.Home.route,
            Screen.Alerts.route,
            Screen.Dashboard.route,
            Screen.Learn.route,
        )
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected =
                            currentDestination?.hierarchy
                                ?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.contentDescription,
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Onboarding.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }
            },
            exitTransition = {
                fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -it / 4 }
            },
            popEnterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 }
            },
            popExitTransition = {
                fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 4 }
            },
        ) {
            // ── Onboarding ──────────────────────────────────────────────────────
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Permissions.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.Permissions.route) {
                PermissionsScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Permissions.route) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main Tabs ───────────────────────────────────────────────────────
            composable(Screen.Home.route) {
                HomeHubScreen(navController = navController)
            }
            composable(Screen.Alerts.route) {
                MessageScanScreen(navController = navController)
            }
            composable(Screen.Dashboard.route) {
                FraudDashboardScreen(navController = navController)
            }
            composable(Screen.Learn.route) {
                AwarenessScreen(navController = navController)
            }

            // ── Feature Screens ─────────────────────────────────────────────────
            composable(Screen.MessageDetail.route) {
                MessageDetailScreen(navController = navController)
            }
            composable(
                route = Screen.RagWarning.route,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://warning/{messageId}" }),
            ) {
                RagWarningScreen(navController = navController)
            }
            composable(Screen.ApkScan.route) {
                ApkScanScreen(navController = navController)
            }
            composable(
                route = Screen.ApkAlert.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://apk_alert/{packageName}" }),
            ) {
                ApkAlertScreen(navController = navController)
            }
            composable(Screen.ConfirmTransfer.route) {
                ConfirmTransferScreen(navController = navController)
            }
            composable(Screen.LivenessCheck.route) {
                LivenessCheckScreen(navController = navController)
            }
            composable(
                route = Screen.NcrpReport.route,
                arguments = listOf(navArgument("contextId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "surakshasathi://report/{contextId}" }),
            ) {
                NcrpReportScreen(navController = navController)
            }
            composable(Screen.OfficialLinkScanner.route) {
                OfficialLinkScannerScreen(navController = navController)
            }
            composable(
                route = Screen.LessonDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
            ) {
                LessonDetailScreen(navController = navController)
            }
            composable(Screen.Badges.route) {
                BadgesScreen(navController = navController)
            }
        }
    }
}
