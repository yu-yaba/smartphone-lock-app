package jp.kawai.ultrafocus.ui.screen

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import jp.kawai.ultrafocus.ui.theme.UltraFocusTheme
import jp.kawai.ultrafocus.ui.theme.radius
import jp.kawai.ultrafocus.ui.theme.spacing
import jp.kawai.ultrafocus.ui.theme.glass
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.kawai.ultrafocus.R
import jp.kawai.ultrafocus.data.repository.DefaultLockPermissionsRepository
import jp.kawai.ultrafocus.model.LockPermissionState
import jp.kawai.ultrafocus.ui.lock.LockScreenViewModel

private const val PREFS_NOTIFICATION_REQUESTED = "prefs_notification_permission"
private const val KEY_NOTIFICATION_REQUESTED = "notification_permission_requested"

@Composable
fun PermissionIntroScreen(
    lockViewModel: LockScreenViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NOTIFICATION_REQUESTED, Context.MODE_PRIVATE)
    }
    val permissionState by lockViewModel.permissionState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    var notificationRequestAttempted by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_NOTIFICATION_REQUESTED, false))
    }
    val showNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val shouldShowNotificationRationale = if (
        showNotificationPermission &&
        !permissionState.notificationGranted &&
        activity != null
    ) {
        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        false
    }
    val notificationNeedsSettings =
        showNotificationPermission &&
            !permissionState.notificationGranted &&
            notificationRequestAttempted &&
            !shouldShowNotificationRationale
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
        notificationRequestAttempted = true
        lockViewModel.refreshPermissions()
    }

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
        notificationNeedsSettings = notificationNeedsSettings,
        onReload = lockViewModel::refreshPermissions,
        onRequestOverlay = {
            launchSafe(context, DefaultLockPermissionsRepository.overlaySettingsIntent(context))
        },
        onRequestUsageStats = {
            launchSafe(context, DefaultLockPermissionsRepository.usageAccessSettingsIntent())
        },
        onRequestExactAlarm = {
            launchExactAlarmSettings(context)
        },
        onRequestNotification = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                lockViewModel.refreshPermissions()
                return@PermissionIntroContent
            }
            if (permissionState.notificationGranted) {
                lockViewModel.refreshPermissions()
                return@PermissionIntroContent
            }
            if (notificationNeedsSettings) {
                launchSafe(context, DefaultLockPermissionsRepository.appDetailsSettingsIntent(context))
            } else {
                prefs.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
                notificationRequestAttempted = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        modifier = modifier
    )
}

@Composable
fun PermissionIntroContent(
    state: LockPermissionState,
    notificationNeedsSettings: Boolean,
    onReload: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageStats: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val showNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val requiredPermissions = if (showNotificationPermission) {
        listOf(
            state.overlayGranted,
            state.usageStatsGranted,
            state.exactAlarmGranted,
            state.notificationGranted
        )
    } else {
        listOf(
            state.overlayGranted,
            state.usageStatsGranted,
            state.exactAlarmGranted
        )
    }
    val grantedCount = requiredPermissions.count { it }
    val totalRequired = requiredPermissions.size
    val progress = grantedCount / totalRequired.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = spacing.lg, vertical = spacing.xxl),
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
        Spacer(modifier = Modifier.height(spacing.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.glass.background,
                    shape = RoundedCornerShape(MaterialTheme.radius.l)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.glass.border,
                    shape = RoundedCornerShape(MaterialTheme.radius.l)
                )
                .padding(horizontal = spacing.lg, vertical = spacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.permission_intro_progress,
                            grantedCount,
                            totalRequired
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
                Text(
                    text = stringResource(id = R.string.permission_intro_progress_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.lg))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .background(
                    color = MaterialTheme.glass.background,
                    shape = RoundedCornerShape(MaterialTheme.radius.l)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.glass.border,
                    shape = RoundedCornerShape(MaterialTheme.radius.l)
                )
                .padding(horizontal = spacing.lg, vertical = spacing.lg)
        ) {
            PermissionList(
                state = state,
                showNotificationPermission = showNotificationPermission,
                notificationNeedsSettings = notificationNeedsSettings,
                onRequestOverlay = onRequestOverlay,
                onRequestUsageStats = onRequestUsageStats,
                onRequestExactAlarm = onRequestExactAlarm,
                onRequestNotification = onRequestNotification,
                modifier = Modifier.fillMaxWidth()
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
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Text(text = stringResource(id = R.string.permission_intro_reload))
        }
    }
}

@Composable
private fun PermissionList(
    state: LockPermissionState,
    showNotificationPermission: Boolean,
    notificationNeedsSettings: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestUsageStats: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val cards = buildList {
        add(
            PermissionCardData(
                title = stringResource(id = R.string.permission_intro_overlay_title),
                description = stringResource(id = R.string.permission_intro_overlay_description),
                granted = state.overlayGranted,
                buttonLabel = stringResource(id = R.string.permission_intro_open_settings),
                onClick = onRequestOverlay
            )
        )
        add(
            PermissionCardData(
                title = stringResource(id = R.string.permission_intro_usage_title),
                description = stringResource(id = R.string.permission_intro_usage_description),
                granted = state.usageStatsGranted,
                buttonLabel = stringResource(id = R.string.permission_intro_open_settings),
                onClick = onRequestUsageStats
            )
        )
        add(
            PermissionCardData(
                title = stringResource(id = R.string.permission_intro_exact_alarm_title),
                description = stringResource(id = R.string.permission_intro_exact_alarm_description),
                granted = state.exactAlarmGranted,
                buttonLabel = stringResource(id = R.string.permission_intro_open_settings),
                onClick = onRequestExactAlarm
            )
        )
        if (showNotificationPermission) {
            add(
                PermissionCardData(
                    title = stringResource(id = R.string.permission_intro_notification_title),
                    description = stringResource(id = R.string.permission_intro_notification_description),
                    granted = state.notificationGranted,
                    buttonLabel = if (notificationNeedsSettings) {
                        stringResource(id = R.string.permission_intro_open_settings)
                    } else {
                        stringResource(id = R.string.permission_intro_notification_button)
                    },
                    onClick = onRequestNotification
                )
            )
        }
    }

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
    val statusColor = if (data.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val badgeColor = statusColor.copy(alpha = 0.12f)
    val statusText = if (data.granted) {
        stringResource(id = R.string.permission_intro_status_granted)
    } else {
        stringResource(id = R.string.permission_intro_status_denied)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.glass.background,
                shape = RoundedCornerShape(MaterialTheme.radius.m)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.glass.border,
                shape = RoundedCornerShape(MaterialTheme.radius.m)
            )
            .padding(horizontal = spacing.lg, vertical = spacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
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
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = data.onClick,
                enabled = !data.granted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(MaterialTheme.radius.s),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, disabledElevation = 0.dp)
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

private fun launchExactAlarmSettings(context: Context) {
    val primary = DefaultLockPermissionsRepository.exactAlarmSettingsIntent(context)
    val fallback = DefaultLockPermissionsRepository.appDetailsSettingsIntent(context)
    primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ContextCompat.startActivity(context, primary, null)
    } catch (error: ActivityNotFoundException) {
        try {
            ContextCompat.startActivity(context, fallback, null)
        } catch (ignored: ActivityNotFoundException) {
            // どちらの設定も存在しない場合は黙って無視する
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionIntroPreviewGranted() {
    UltraFocusTheme {
        PermissionIntroContent(
            state = LockPermissionState(
                overlayGranted = true,
                usageStatsGranted = true,
                exactAlarmGranted = true,
                notificationGranted = true
            ),
            notificationNeedsSettings = false,
            onReload = {},
            onRequestOverlay = {},
            onRequestUsageStats = {},
            onRequestExactAlarm = {},
            onRequestNotification = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionIntroPreviewMissing() {
    UltraFocusTheme {
        PermissionIntroContent(
            state = LockPermissionState(
                overlayGranted = false,
                usageStatsGranted = false,
                exactAlarmGranted = false,
                notificationGranted = false
            ),
            notificationNeedsSettings = false,
            onReload = {},
            onRequestOverlay = {},
            onRequestUsageStats = {},
            onRequestExactAlarm = {},
            onRequestNotification = {}
        )
    }
}
