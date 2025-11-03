package com.example.smartphone_lock.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphone_lock.R
import com.example.smartphone_lock.ui.lock.LockScreenViewModel

@Composable
fun PermissionScreen(
    lockViewModel: LockScreenViewModel,
    modifier: Modifier = Modifier
) {
    val permissionState by lockViewModel.permissionState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(id = R.string.permission_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.permission_screen_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))

        PermissionStatusRow(
            label = stringResource(id = R.string.permission_screen_overlay_label),
            granted = permissionState.overlayGranted
        )
        Spacer(modifier = Modifier.height(16.dp))
        PermissionStatusRow(
            label = stringResource(id = R.string.permission_screen_usage_label),
            granted = permissionState.usageStatsGranted
        )
        Spacer(modifier = Modifier.height(16.dp))
        PermissionStatusRow(
            label = stringResource(id = R.string.permission_screen_notification_label),
            granted = permissionState.notificationAccessGranted
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = lockViewModel::refreshPermissions) {
            Text(text = stringResource(id = R.string.permission_screen_button))
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (granted) {
                stringResource(id = R.string.permission_screen_status_granted)
            } else {
                stringResource(id = R.string.permission_screen_status_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}
