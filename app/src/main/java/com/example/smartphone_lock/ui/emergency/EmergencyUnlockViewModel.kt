package com.example.smartphone_lock.ui.emergency

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class EmergencyUnlockViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUnlockUiState())
    val uiState: StateFlow<EmergencyUnlockUiState> = _uiState.asStateFlow()

    fun onInputChanged(newText: String) {
        _uiState.update { current ->
            current.copy(
                inputText = newText,
                mismatchIndex = findMismatchIndex(current.declarationText, newText)
            )
        }
    }

    fun reset() {
        _uiState.update { EmergencyUnlockUiState() }
    }

    private fun findMismatchIndex(expected: String, actual: String): Int? {
        if (actual.isEmpty()) return null
        val minLength = minOf(expected.length, actual.length)
        for (index in 0 until minLength) {
            if (expected[index] != actual[index]) {
                return index
            }
        }
        return when {
            actual.length == expected.length -> null
            actual.length < expected.length -> actual.length
            else -> expected.length
        }
    }
}

data class EmergencyUnlockUiState(
    val declarationText: String = EMERGENCY_UNLOCK_DECLARATION_V1,
    val inputText: String = "",
    val mismatchIndex: Int? = null
) {
    val isMatched: Boolean get() = inputText == declarationText
}
