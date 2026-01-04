package jp.kawai.ultrafocus.ui.emergency

data class EmergencyUnlockUiState(
    val inputText: String = "",
    val isMatched: Boolean = false,
    val mismatchIndex: Int? = null
) {
    val inputLength: Int
        get() = inputText.length
}
