package com.example.smartphone_lock.ui.lock

import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.repository.LockPermissionsRepository
import com.example.smartphone_lock.data.repository.LockRepository
import com.example.smartphone_lock.model.LockPermissionState
import com.example.smartphone_lock.service.LockMonitorService
import com.example.smartphone_lock.service.OverlayLockService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class LockScreenViewModel @Inject constructor(
    private val lockPermissionsRepository: LockPermissionsRepository,
    private val dataStoreManager: DataStoreManager,
    private val lockRepository: LockRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val permissionState: StateFlow<LockPermissionState> = lockPermissionsRepository.permissionStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LockPermissionState()
        )

    private val _uiState = MutableStateFlow(LockScreenUiState())
    val uiState: StateFlow<LockScreenUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var pendingLockActivationUntil: Long? = null
    private var overlayRunning: Boolean = false

    init {
        refreshPermissions()
        observeLockState()
    }

    private fun observeLockState() {
        viewModelScope.launch {
            combine(
                dataStoreManager.isLocked,
                dataStoreManager.lockStartTimestamp,
                dataStoreManager.lockEndTimestamp,
                dataStoreManager.selectedDurationHours,
                dataStoreManager.selectedDurationMinutes
            ) { isLocked, lockStartTimestamp, lockEndTimestamp, selectedHours, selectedMinutes ->
                LockPreferencesState(
                    isLocked,
                    lockStartTimestamp,
                    lockEndTimestamp,
                    selectedHours,
                    selectedMinutes
                )
            }.collect { state ->
                val (normalizedHours, normalizedMinutes) = normalizeDuration(
                    state.selectedHours,
                    state.selectedMinutes
                )

                if (state.selectedHours != normalizedHours || state.selectedMinutes != normalizedMinutes) {
                    dataStoreManager.updateSelectedDuration(normalizedHours, normalizedMinutes)
                }

                val now = System.currentTimeMillis()
                val storeRemainingMillis = if (state.lockEndTimestamp != null) {
                    (state.lockEndTimestamp - now).coerceAtLeast(0L)
                } else {
                    0L
                }

                var effectiveRemainingMillis = storeRemainingMillis
                var effectiveIsLocked = state.isLocked && storeRemainingMillis > 0L
                var countdownTarget: Long? = null

                if (state.isLocked) {
                    pendingLockActivationUntil = null
                    if (state.lockEndTimestamp != null && storeRemainingMillis > 0L) {
                        countdownTarget = state.lockEndTimestamp
                    }
                } else {
                    val pendingUntil = pendingLockActivationUntil
                    if (pendingUntil != null) {
                        val pendingRemaining = (pendingUntil - now).coerceAtLeast(0L)
                        if (pendingRemaining > 0L) {
                            effectiveIsLocked = true
                            effectiveRemainingMillis = pendingRemaining
                            countdownTarget = pendingUntil
                        } else {
                            pendingLockActivationUntil = null
                        }
                    }
                }

                _uiState.update { current ->
                    current.copy(
                        isLocked = effectiveIsLocked,
                        selectedHours = normalizedHours,
                        selectedMinutes = normalizedMinutes,
                        remainingMillis = effectiveRemainingMillis
                    )
                }

                val shouldRunOverlay = effectiveIsLocked && countdownTarget != null

                if (!effectiveIsLocked) {
                    countdownJob?.cancel()
                } else if (countdownTarget != null) {
                    countdownJob?.cancel()
                    startCountdown(countdownTarget)
                }

                if (overlayRunning != shouldRunOverlay) {
                    overlayRunning = shouldRunOverlay
                    if (shouldRunOverlay) {
                        LockMonitorService.start(appContext)
                        OverlayLockService.start(appContext)
                    } else {
                        OverlayLockService.stop(appContext)
                        LockMonitorService.stop(appContext)
                    }
                }
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            lockPermissionsRepository.refreshPermissionState()
        }
    }

    fun updateSelectedDuration(hours: Int, minutes: Int) {
        val (normalizedHours, normalizedMinutes) = normalizeDuration(hours, minutes)
        val current = _uiState.value
        if (current.selectedHours == normalizedHours && current.selectedMinutes == normalizedMinutes) {
            return
        }
        _uiState.update {
            it.copy(selectedHours = normalizedHours, selectedMinutes = normalizedMinutes)
        }
        viewModelScope.launch {
            dataStoreManager.updateSelectedDuration(normalizedHours, normalizedMinutes)
        }
    }

    fun startLock(activity: Activity?) {
        val permissions = permissionState.value
        if (!permissions.allGranted) {
            Log.w(TAG, "Cannot start lock: missing permissions $permissions")
            return
        }
        if (!ensureNotificationPermission(activity)) {
            Log.w(TAG, "Notification permission request in-flight; postponing lock start")
            return
        }
        lockRepository.refreshDynamicLists()
        val selectedState = uiState.value
        val (hours, minutes) = normalizeDuration(selectedState.selectedHours, selectedState.selectedMinutes)
        val lockStartTimestamp = System.currentTimeMillis()
        val lockEndTimestamp = lockStartTimestamp +
            TimeUnit.HOURS.toMillis(hours.toLong()) +
            TimeUnit.MINUTES.toMillis(minutes.toLong())
        countdownJob?.cancel()
        pendingLockActivationUntil = lockEndTimestamp
        _uiState.update { current ->
            current.copy(
                isLocked = true,
                selectedHours = hours,
                selectedMinutes = minutes,
                remainingMillis = lockEndTimestamp - System.currentTimeMillis()
            )
        }
        viewModelScope.launch {
            dataStoreManager.updateLockState(true, lockStartTimestamp, lockEndTimestamp)
            LockMonitorService.start(appContext)
            OverlayLockService.start(appContext)
            overlayRunning = true
        }
    }

    fun stopLock() {
        countdownJob?.cancel()
        pendingLockActivationUntil = null
        _uiState.update { current ->
            current.copy(isLocked = false, remainingMillis = 0L)
        }
        viewModelScope.launch {
            dataStoreManager.updateLockState(false, null, null)
            OverlayLockService.stop(appContext)
            LockMonitorService.stop(appContext)
            overlayRunning = false
        }
    }

    private fun startCountdown(lockEndTimestamp: Long) {
        countdownJob = viewModelScope.launch {
            while (true) {
                val remainingMillis = (lockEndTimestamp - System.currentTimeMillis()).coerceAtLeast(0L)
                _uiState.update { current ->
                    current.copy(
                        isLocked = remainingMillis > 0L,
                        remainingMillis = remainingMillis
                    )
                }
                if (remainingMillis <= 0L) {
                    onCountdownFinished()
                    break
                }
                delay(ONE_SECOND_MILLIS)
            }
        }
    }

    private fun onCountdownFinished() {
        stopLock()
    }

    private fun ensureNotificationPermission(activity: Activity?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        val permissionState = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (activity == null) {
            return false
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_POST_NOTIFICATIONS
        )
        return false
    }

    companion object {
        const val MIN_DURATION_HOURS = 0
        const val MAX_DURATION_HOURS = 72
        const val MINUTE_INCREMENT = 1
        private const val ONE_SECOND_MILLIS = 1_000L
        private const val TAG = "LockScreenViewModel"
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }

    private fun normalizeDuration(hours: Int, minutes: Int): Pair<Int, Int> {
        val clampedHours = hours.coerceIn(MIN_DURATION_HOURS, MAX_DURATION_HOURS)
        val clampedMinutes = minutes.coerceIn(0, 59)
        val minTotalMinutes = 1
        val maxTotalMinutes = MAX_DURATION_HOURS * 60

        var totalMinutes = clampedHours * 60 + clampedMinutes
        if (totalMinutes < minTotalMinutes) {
            totalMinutes = minTotalMinutes
        }
        if (totalMinutes > maxTotalMinutes) {
            totalMinutes = maxTotalMinutes
        }

        val normalizedHours = totalMinutes / 60
        val normalizedMinutes = totalMinutes % 60

        return normalizedHours to normalizedMinutes
    }
}

data class LockScreenUiState(
    val isLocked: Boolean = false,
    val selectedHours: Int = DataStoreManager.DEFAULT_DURATION_HOURS,
    val selectedMinutes: Int = DataStoreManager.DEFAULT_DURATION_MINUTES,
    val remainingMillis: Long = 0L
)

private data class LockPreferencesState(
    val isLocked: Boolean,
    val lockStartTimestamp: Long?,
    val lockEndTimestamp: Long?,
    val selectedHours: Int,
    val selectedMinutes: Int
)
