package com.example.smartphone_lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartphone_lock.navigation.AppDestination
import com.example.smartphone_lock.ui.screen.AuthScreen
import com.example.smartphone_lock.ui.screen.CompleteScreen
import com.example.smartphone_lock.ui.screen.HomeScreen
import com.example.smartphone_lock.ui.screen.LockSettingScreen
import com.example.smartphone_lock.ui.screen.LockScreen
import com.example.smartphone_lock.ui.screen.PermissionScreen
import com.example.smartphone_lock.ui.lock.LockViewModel

@Composable
fun SmartphoneLockApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val lockViewModel: LockViewModel = hiltViewModel()
    val isAdminActive = lockViewModel.isAdminActive.collectAsStateWithLifecycle()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value

    LaunchedEffect(Unit) {
        lockViewModel.refreshAdminState()
    }

    LaunchedEffect(isAdminActive.value, currentBackStackEntry?.destination?.route) {
        val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        val target = if (isAdminActive.value) {
            AppDestination.Lock.route
        } else {
            AppDestination.Permission.route
        }
        if (currentRoute != target) {
            navController.navigateAndSetAsRoot(target)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.Permission.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestination.Permission.route) {
                PermissionScreen(lockViewModel = lockViewModel)
            }

            composable(AppDestination.Lock.route) {
                LockScreen(lockViewModel = lockViewModel)
            }

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

private fun androidx.navigation.NavController.navigateAndPopUpToRoot(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(graph.startDestinationId) {
            inclusive = false
            saveState = false
        }
        restoreState = false
    }
}

private fun androidx.navigation.NavController.navigateAndSetAsRoot(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(graph.startDestinationId) {
            inclusive = true
            saveState = false
        }
        restoreState = false
    }
}
