package jp.kawai.ultrafocus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.kawai.ultrafocus.emergency.EmergencyUnlockState
import jp.kawai.ultrafocus.navigation.AppDestination
import jp.kawai.ultrafocus.ui.lock.LockScreenViewModel
import jp.kawai.ultrafocus.ui.screen.EmergencyUnlockScreen
import jp.kawai.ultrafocus.ui.screen.LockScreen
import jp.kawai.ultrafocus.ui.screen.PermissionIntroScreen
import jp.kawai.ultrafocus.ui.theme.GradientSkyEnd
import jp.kawai.ultrafocus.ui.theme.GradientSkyStart

@Composable
fun UltraFocusApp(
    modifier: Modifier = Modifier,
    lockViewModel: LockScreenViewModel = hiltViewModel(),
    requestedNavRoute: String? = null,
    onRequestedNavRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val permissionState = lockViewModel.permissionState.collectAsStateWithLifecycle()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val startDestination = if (permissionState.value.allGranted) {
        AppDestination.Lock.route
    } else {
        AppDestination.Permission.route
    }

    LaunchedEffect(Unit) {
        lockViewModel.refreshPermissions()
    }

    LaunchedEffect(permissionState.value.allGranted, currentRoute) {
        val target = determinePermissionDestination(currentRoute, permissionState.value.allGranted)
        if (target != null) {
            navController.navigateAndSetAsRoot(target)
        }
    }

    LaunchedEffect(currentRoute, requestedNavRoute, permissionState.value.allGranted) {
        if (!permissionState.value.allGranted) {
            if (requestedNavRoute != null) {
                onRequestedNavRouteConsumed()
            }
            EmergencyUnlockState.setActive(false)
            return@LaunchedEffect
        }
        val emergencyRequested = requestedNavRoute == AppDestination.EmergencyUnlock.route
        val emergencyVisible = currentRoute == AppDestination.EmergencyUnlock.route
        EmergencyUnlockState.setActive(emergencyVisible || emergencyRequested)
    }

    LaunchedEffect(requestedNavRoute, permissionState.value.allGranted) {
        val targetRoute = requestedNavRoute
        if (targetRoute != null && permissionState.value.allGranted) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
            onRequestedNavRouteConsumed()
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
                LockScreen(lockViewModel = lockViewModel)
            }

            composable(AppDestination.EmergencyUnlock.route) {
                EmergencyUnlockScreen(
                    onBackToLock = { navController.navigateAndSetAsRoot(AppDestination.Lock.route) },
                    onUnlocked = { navController.navigateAndSetAsRoot(AppDestination.Lock.route) }
                )
            }
        }
    }
}

internal fun determinePermissionDestination(
    currentRoute: String?,
    allGranted: Boolean
): String? {
    if (!allGranted) {
        return if (currentRoute == AppDestination.Permission.route) null else AppDestination.Permission.route
    }
    if (currentRoute == null || currentRoute == AppDestination.Permission.route) {
        return AppDestination.Lock.route
    }
    return null
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
