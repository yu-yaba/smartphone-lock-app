package com.example.smartphone_lock.ui.lock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartphone_lock.R
import com.example.smartphone_lock.SmartphoneLockDeviceAdminReceiver
import com.example.smartphone_lock.data.repository.AdminPermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devicePolicyManager: DevicePolicyManager,
    private val adminPermissionRepository: AdminPermissionRepository
) : ViewModel() {

    private val deviceAdminComponent = ComponentName(context, SmartphoneLockDeviceAdminReceiver::class.java)
    val isAdminActive: StateFlow<Boolean> = adminPermissionRepository.isAdminGrantedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = devicePolicyManager.isAdminActive(deviceAdminComponent)
        )

    init {
        refreshAdminState()
    }

    fun refreshAdminState() {
        viewModelScope.launch {
            val active = devicePolicyManager.isAdminActive(deviceAdminComponent)
            adminPermissionRepository.setAdminGranted(active)
        }
    }

    fun onAdminPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                val active = devicePolicyManager.isAdminActive(deviceAdminComponent)
                adminPermissionRepository.setAdminGranted(active)
            } else {
                adminPermissionRepository.setAdminGranted(false)
            }
        }
    }

    fun buildAddDeviceAdminIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.permission_screen_body))
    }

    fun startLockTask(activity: Activity) {
        if (!devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            Log.w(TAG, "Cannot start lock task without active admin")
            return
        }
        runCatching {
            if (devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
                devicePolicyManager.setLockTaskPackages(
                    deviceAdminComponent,
                    arrayOf(activity.packageName)
                )
            }
            activity.startLockTask()
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to start lock task", throwable)
        }
    }

    fun stopLockTask(activity: Activity) {
        runCatching {
            activity.stopLockTask()
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to stop lock task", throwable)
        }
    }

    companion object {
        private const val TAG = "LockViewModel"
    }
}
