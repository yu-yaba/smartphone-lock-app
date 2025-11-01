package com.example.smartphone_lock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smartphone_lock.data.repository.AdminPermissionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Device admin callbacks for the smartphone lock experience.
 */
@AndroidEntryPoint
class SmartphoneLockDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject
    lateinit var adminPermissionRepository: AdminPermissionRepository

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        persistAdminState(active = true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled")
        persistAdminState(active = false)
    }

    private fun persistAdminState(active: Boolean) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                adminPermissionRepository.setAdminGranted(active)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to persist admin state", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "LockDeviceAdmin"
    }
}
