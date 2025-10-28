package com.example.smartphone_lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartphone_lock.navigation.AppDestination
import com.example.smartphone_lock.ui.screen.AuthScreen
import com.example.smartphone_lock.ui.screen.CompleteScreen
import com.example.smartphone_lock.ui.screen.HomeScreen
import com.example.smartphone_lock.ui.screen.LockSettingScreen

@Composable
fun SmartphoneLockApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    onNavigateToLockSetting = {
                        navController.navigateAndPopUpToRoot(AppDestination.LockSetting.route)
                    },
                    onNavigateToAuth = {
                        navController.navigateAndPopUpToRoot(AppDestination.Auth.route)
                    },
                    onNavigateToComplete = {
                        navController.navigateAndPopUpToRoot(AppDestination.Complete.route)
                    }
                )
            }

            composable(AppDestination.LockSetting.route) {
                LockSettingScreen(
                    onNavigateToHome = {
                        navController.navigateAndPopUpToRoot(AppDestination.Home.route)
                    },
                    onNavigateToAuth = {
                        navController.navigateAndPopUpToRoot(AppDestination.Auth.route)
                    },
                    onNavigateToComplete = {
                        navController.navigateAndPopUpToRoot(AppDestination.Complete.route)
                    }
                )
            }

            composable(AppDestination.Auth.route) {
                AuthScreen(
                    onNavigateToHome = {
                        navController.navigateAndPopUpToRoot(AppDestination.Home.route)
                    },
                    onNavigateToLockSetting = {
                        navController.navigateAndPopUpToRoot(AppDestination.LockSetting.route)
                    },
                    onNavigateToComplete = {
                        navController.navigateAndPopUpToRoot(AppDestination.Complete.route)
                    }
                )
            }

            composable(AppDestination.Complete.route) {
                CompleteScreen(
                    onNavigateToHome = {
                        navController.navigateAndPopUpToRoot(AppDestination.Home.route)
                    },
                    onNavigateToLockSetting = {
                        navController.navigateAndPopUpToRoot(AppDestination.LockSetting.route)
                    },
                    onNavigateToAuth = {
                        navController.navigateAndPopUpToRoot(AppDestination.Auth.route)
                    }
                )
            }
        }
    }
}

private fun NavController.navigateAndPopUpToRoot(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(graph.startDestinationId) {
            inclusive = false
            saveState = false
        }
        restoreState = false
    }
}
