package com.example.smartphone_lock.ui.screen

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphone_lock.R
import com.example.smartphone_lock.ui.lock.LockViewModel

@Composable
fun LockScreen(
    lockViewModel: LockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isAdminActive = lockViewModel.isAdminActive.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.lock_screen_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                id = if (isAdminActive.value) {
                    R.string.lock_screen_status_enabled
                } else {
                    R.string.lock_screen_status_disabled
                }
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                activity?.let(lockViewModel::startLockTask)
            },
            enabled = activity != null && isAdminActive.value
        ) {
            Text(text = stringResource(id = R.string.lock_screen_start))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                activity?.let(lockViewModel::stopLockTask)
            },
            enabled = activity != null
        ) {
            Text(text = stringResource(id = R.string.lock_screen_stop))
        }
    }
}
