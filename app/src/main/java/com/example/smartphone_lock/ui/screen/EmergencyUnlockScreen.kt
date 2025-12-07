package com.example.smartphone_lock.ui.screen

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import com.example.smartphone_lock.BuildConfig
import com.example.smartphone_lock.R
import com.example.smartphone_lock.ui.emergency.EmergencyUnlockViewModel
import com.example.smartphone_lock.ui.lock.LockScreenViewModel
import com.example.smartphone_lock.ui.theme.glass
import com.example.smartphone_lock.ui.theme.gradients
import com.example.smartphone_lock.ui.theme.radius
import com.example.smartphone_lock.ui.theme.spacing

@Composable
fun EmergencyUnlockScreen(
    lockViewModel: LockScreenViewModel,
    emergencyUnlockViewModel: EmergencyUnlockViewModel,
    onBackToLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by emergencyUnlockViewModel.uiState.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing
    val rootScroll = rememberScrollState()

    BackHandler {
        emergencyUnlockViewModel.reset()
        onBackToLock()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.gradients.skyDawn)
            .padding(horizontal = spacing.xl, vertical = spacing.xxl)
            .verticalScroll(rootScroll),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(id = R.string.emergency_unlock_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(id = R.string.emergency_unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
        }

        DeclarationCard(text = uiState.declarationText)

        InputCard(
            value = uiState.inputText,
            onValueChange = emergencyUnlockViewModel::onInputChanged
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = stringResource(
                    id = R.string.emergency_unlock_progress,
                    uiState.inputText.length,
                    uiState.declarationText.length
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f)
            )

            Text(
                text = stringResource(id = R.string.emergency_unlock_hint_exact_match),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
            )

            val mismatchIndex = uiState.mismatchIndex
            if (mismatchIndex != null) {
                Text(
                    text = stringResource(
                        id = R.string.emergency_unlock_mismatch_from_index,
                        mismatchIndex + 1
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        Button(
            onClick = {
                lockViewModel.stopLock()
                emergencyUnlockViewModel.reset()
                onBackToLock()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = uiState.isMatched,
            shape = RoundedCornerShape(MaterialTheme.radius.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = stringResource(id = R.string.emergency_unlock_action_unlock),
                style = MaterialTheme.typography.titleMedium
            )
        }

        OutlinedButton(
            onClick = {
                emergencyUnlockViewModel.reset()
                onBackToLock()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(MaterialTheme.radius.pill),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Text(
                text = stringResource(id = R.string.emergency_unlock_action_back),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun DeclarationCard(text: String, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val shape = RoundedCornerShape(MaterialTheme.radius.l)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 360.dp)
            .background(MaterialTheme.glass.background, shape)
            .border(1.dp, MaterialTheme.glass.border, shape)
            .padding(spacing.lg)
    ) {
        val scrollState = rememberScrollState()
        DisableSelection {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}

@Composable
private fun InputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    val shape = RoundedCornerShape(MaterialTheme.radius.l)
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f).toArgb()
    val backgroundColor = MaterialTheme.glass.background.toArgb()
    val paddingPx = with(density) { spacing.md.roundToPx() }

    val allowPaste = BuildConfig.DEBUG

    AndroidView(
        factory = { context ->
            NoPasteEditText(context, allowPaste = allowPaste).apply {
                setText(value)
                setTextColor(onSurface)
                setHintTextColor(hintColor)
                hint = context.getString(R.string.emergency_unlock_input_hint)
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 5
                setLineSpacing(0f, 1.08f)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setBackgroundColor(backgroundColor)
                doOnTextChanged { text, _, _, _ ->
                    onValueChange(text?.toString() ?: "")
                }
            }
        },
        update = { editText ->
            if (editText.text.toString() != value) {
                val selectionStart = editText.selectionStart.coerceAtMost(value.length)
                editText.setText(value)
                editText.setSelection(selectionStart)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 460.dp)
            .background(MaterialTheme.glass.background, shape)
            .border(1.dp, MaterialTheme.glass.border, shape)
    )
}

private class NoPasteEditText(
    context: Context,
    private val allowPaste: Boolean
) : EditText(context) {
    init {
        if (!allowPaste) {
            setTextIsSelectable(false)
            isLongClickable = false
            setOnLongClickListener { true }
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (allowPaste) return super.onTextContextMenuItem(id)
        return when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> false
            else -> super.onTextContextMenuItem(id)
        }
    }

    override fun isSuggestionsEnabled(): Boolean = false
}
