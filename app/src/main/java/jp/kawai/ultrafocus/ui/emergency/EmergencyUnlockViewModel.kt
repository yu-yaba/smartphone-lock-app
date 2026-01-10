package jp.kawai.ultrafocus.ui.emergency

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.emergency.EMERGENCY_UNLOCK_DECLARATION_V1
import jp.kawai.ultrafocus.emergency.EmergencyUnlockStateStore
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import jp.kawai.ultrafocus.service.WatchdogScheduler
import jp.kawai.ultrafocus.service.WatchdogWorkScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EmergencyUnlockViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUnlockUiState())
    val uiState: StateFlow<EmergencyUnlockUiState> = _uiState.asStateFlow()

    private val _unlockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unlockEvents = _unlockEvents.asSharedFlow()

    fun updateInput(text: String) {
        val mismatchIndex = findMismatchIndex(text, EMERGENCY_UNLOCK_DECLARATION_V1)
        val matched = text == EMERGENCY_UNLOCK_DECLARATION_V1
        _uiState.update { current ->
            current.copy(
                inputText = text,
                isMatched = matched,
                mismatchIndex = mismatchIndex
            )
        }
    }

    fun resetInput() {
        updateInput("")
    }

    fun requestUnlock() {
        if (!_uiState.value.isMatched) return
        viewModelScope.launch {
            EmergencyUnlockStateStore.setActive(appContext, false)
            dataStoreManager.updateLockState(false, null, null)
            OverlayLockService.stop(appContext)
            LockMonitorService.stop(appContext)
            WatchdogScheduler.cancelHeartbeat(appContext)
            WatchdogScheduler.cancelLockExpiry(appContext)
            WatchdogWorkScheduler.cancel(appContext)
            _unlockEvents.tryEmit(Unit)
        }
    }

    private fun findMismatchIndex(input: String, target: String): Int? {
        val limit = minOf(input.length, target.length)
        for (index in 0 until limit) {
            if (input[index] != target[index]) {
                return index
            }
        }
        return if (input.length > target.length) target.length else null
    }
}
