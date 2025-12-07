package com.example.smartphone_lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartphone_lock.navigation.AppDestination
import com.example.smartphone_lock.ui.emergency.EmergencyUnlockViewModel
import com.example.smartphone_lock.ui.lock.LockScreenViewModel
import com.example.smartphone_lock.ui.screen.EmergencyUnlockScreen
import com.example.smartphone_lock.ui.screen.LockScreen
import com.example.smartphone_lock.ui.screen.PermissionIntroScreen
import com.example.smartphone_lock.ui.theme.GradientSkyEnd
import com.example.smartphone_lock.ui.theme.GradientSkyStart

@Composable
fun SmartphoneLockApp(
    modifier: Modifier = Modifier,
    lockViewModel: LockScreenViewModel = hiltViewModel(),
    initialRoute: InitialRoute? = null
) {
    val navController = rememberNavController()
    val permissionState = lockViewModel.permissionState.collectAsStateWithLifecycle()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val initialRouteConsumed = remember(initialRoute?.nonce) { mutableStateOf(initialRoute?.route.isNullOrBlank()) }
    val initialRouteValue = initialRoute?.route
    val startDestination = when {
        !permissionState.value.allGranted -> AppDestination.Permission.route
        !initialRouteValue.isNullOrBlank() -> initialRouteValue
        else -> AppDestination.Lock.route
    }

    LaunchedEffect(Unit) {
        lockViewModel.refreshPermissions()
    }

    LaunchedEffect(initialRouteValue, initialRoute?.nonce, permissionState.value.allGranted) {
        val route = initialRouteValue
        if (permissionState.value.allGranted && !route.isNullOrBlank()) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = false
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                    saveState = false
                }
            }
            initialRouteConsumed.value = true
        }
    }

    LaunchedEffect(
        permissionState.value.allGranted,
        currentBackStackEntry?.destination?.route,
        initialRouteConsumed.value,
        initialRoute?.nonce
    ) {
        if (!initialRouteConsumed.value && permissionState.value.allGranted) {
            // 初回の緊急解除ルート遷移を優先し、強制リダイレクトを抑制
            return@LaunchedEffect
        }
        val currentRoute = currentBackStackEntry?.destination?.route
        val target = determinePermissionDestination(currentRoute, permissionState.value.allGranted)
        if (target != null) {
            navController.navigateAndSetAsRoot(target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientSkyEnd,
                        GradientSkyStart
                    )
                )
            )
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestination.Permission.route) {
                PermissionIntroScreen(lockViewModel = lockViewModel)
            }

            composable(AppDestination.Lock.route) {
                LockScreen(
                    lockViewModel = lockViewModel,
                    onNavigateToEmergencyUnlock = {
                        if (navController.currentDestination?.route != AppDestination.EmergencyUnlock.route) {
                            navController.navigate(AppDestination.EmergencyUnlock.route)
                        }
                    }
                )
            }

            composable(AppDestination.EmergencyUnlock.route) {
                val emergencyUnlockViewModel: EmergencyUnlockViewModel = hiltViewModel()
                EmergencyUnlockScreen(
                    lockViewModel = lockViewModel,
                    emergencyUnlockViewModel = emergencyUnlockViewModel,
                    onBackToLock = {
                        if (!navController.popBackStack()) {
                            navController.navigate(AppDestination.Lock.route)
                        }
                    }
                )
            }
        }
    }
}

internal fun determinePermissionDestination(
    currentRoute: String?,
    allGranted: Boolean
): String? {
    return when {
        allGranted && currentRoute == AppDestination.Permission.route -> AppDestination.Lock.route
        !allGranted && currentRoute != AppDestination.Permission.route -> AppDestination.Permission.route
        currentRoute == null -> if (allGranted) AppDestination.Lock.route else AppDestination.Permission.route
        else -> null
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
