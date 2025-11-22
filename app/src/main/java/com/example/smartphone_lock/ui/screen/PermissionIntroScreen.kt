package com.example.smartphone_lock.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphone_lock.R
import com.example.smartphone_lock.data.repository.DefaultLockPermissionsRepository
import com.example.smartphone_lock.model.LockPermissionState
import com.example.smartphone_lock.ui.lock.LockScreenViewModel

@Composable
fun PermissionIntroScreen(
    lockViewModel: LockScreenViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val permissionState by lockViewModel.permissionState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lockViewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PermissionIntroContent(
        state = permissionState,
        onReload = lockViewModel::refreshPermissions,
        onRequestOverlay = {
            launchSafe(context, DefaultLockPermissionsRepository.overlaySettingsIntent(context))
        },
        onRequestUsageStats = {
            launchSafe(context, DefaultLockPermissionsRepository.usageAccessSettingsIntent())
        },
        modifier = modifier
    )
}

@Composable
fun PermissionIntroContent(
    state: LockPermissionState,
    onReload: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(id = R.string.permission_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.permission_intro_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    PermissionList(
        state = state,
        onRequestOverlay = onRequestOverlay,
        onRequestUsageStats = onRequestUsageStats,
        modifier = Modifier.weight(1f, fill = false)
    )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onReload, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.permission_intro_reload))
        }
    }
}

@Composable
private fun PermissionList(
    state: LockPermissionState,
    onRequestOverlay: () -> Unit,
    onRequestUsageStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = listOf(
        PermissionCardData(
            title = stringResource(id = R.string.permission_intro_overlay_title),
            description = stringResource(id = R.string.permission_intro_overlay_description),
            granted = state.overlayGranted,
            buttonLabel = stringResource(id = R.string.permission_intro_open_settings),
            onClick = onRequestOverlay
        ),
        PermissionCardData(
            title = stringResource(id = R.string.permission_intro_usage_title),
            description = stringResource(id = R.string.permission_intro_usage_description),
            granted = state.usageStatsGranted,
            buttonLabel = stringResource(id = R.string.permission_intro_open_settings),
            onClick = onRequestUsageStats
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(cards) { card ->
            PermissionCard(card)
        }
    }
}

@Composable
private fun PermissionCard(data: PermissionCardData) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (data.granted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (data.granted) {
                    stringResource(id = R.string.permission_intro_status_granted)
                } else {
                    stringResource(id = R.string.permission_intro_status_denied)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (data.granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = data.onClick,
                enabled = !data.granted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = data.buttonLabel)
            }
        }
    }
}

private data class PermissionCardData(
    val title: String,
    val description: String,
    val granted: Boolean,
    val buttonLabel: String,
    val onClick: () -> Unit
)

private fun launchSafe(context: Context, intent: Intent) {
    val fallback = Intent(Settings.ACTION_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ContextCompat.startActivity(context, intent, null)
    } catch (error: ActivityNotFoundException) {
        ContextCompat.startActivity(context, fallback, null)
    }
}
