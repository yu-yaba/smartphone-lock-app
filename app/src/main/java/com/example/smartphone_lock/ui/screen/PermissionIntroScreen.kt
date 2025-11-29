package com.example.smartphone_lock.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import com.example.smartphone_lock.ui.theme.elevations
import com.example.smartphone_lock.ui.theme.gradients
import com.example.smartphone_lock.ui.theme.radius
import com.example.smartphone_lock.ui.theme.spacing
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
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.gradients.skyDawn)
            .padding(horizontal = spacing.xl, vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(id = R.string.permission_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = stringResource(id = R.string.permission_intro_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
        )
        Spacer(modifier = Modifier.height(spacing.xl))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            shape = RoundedCornerShape(MaterialTheme.radius.l),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = MaterialTheme.elevations.level1)
        ) {
            PermissionList(
                state = state,
                onRequestOverlay = onRequestOverlay,
                onRequestUsageStats = onRequestUsageStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.lg)
            )
        }
        Spacer(modifier = Modifier.height(spacing.lg))
        Button(
            onClick = onReload,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(MaterialTheme.radius.s),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = MaterialTheme.elevations.level1)
        ) {
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
    val spacing = MaterialTheme.spacing
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
        contentPadding = PaddingValues(vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        items(cards) { card ->
            PermissionCard(card)
        }
    }
}

@Composable
private fun PermissionCard(data: PermissionCardData) {
    val spacing = MaterialTheme.spacing
    val statusColor = if (data.granted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val badgeColor = statusColor.copy(alpha = 0.12f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = MaterialTheme.elevations.level1),
        shape = RoundedCornerShape(MaterialTheme.radius.m),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = badgeColor,
                            shape = RoundedCornerShape(MaterialTheme.radius.s)
                        )
                        .padding(horizontal = spacing.md, vertical = spacing.xs)
                ) {
                    Text(
                        text = if (data.granted) {
                            stringResource(id = R.string.permission_intro_status_granted)
                        } else {
                            stringResource(id = R.string.permission_intro_status_denied)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(spacing.lg))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(spacing.lg))
            Button(
                onClick = data.onClick,
                enabled = !data.granted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(MaterialTheme.radius.s),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = MaterialTheme.elevations.level1,
                    disabledElevation = 0.dp
                )
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

@Preview(showBackground = true)
@Composable
private fun PermissionIntroPreviewGranted() {
    SmartphoneLockTheme {
        PermissionIntroContent(
            state = LockPermissionState(overlayGranted = true, usageStatsGranted = true),
            onReload = {},
            onRequestOverlay = {},
            onRequestUsageStats = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionIntroPreviewMissing() {
    SmartphoneLockTheme {
        PermissionIntroContent(
            state = LockPermissionState(overlayGranted = false, usageStatsGranted = false),
            onReload = {},
            onRequestOverlay = {},
            onRequestUsageStats = {}
        )
    }
}
