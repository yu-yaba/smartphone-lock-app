package jp.kawai.ultrafocus.emergency

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmergencyUnlockState {
    private val activeState = MutableStateFlow(false)

    val active: StateFlow<Boolean> = activeState.asStateFlow()

    fun setActive(active: Boolean) {
        activeState.value = active
    }

    fun isActive(): Boolean = activeState.value
}
