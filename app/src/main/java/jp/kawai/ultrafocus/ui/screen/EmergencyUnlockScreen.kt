package jp.kawai.ultrafocus.ui.screen

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.kawai.ultrafocus.R
import jp.kawai.ultrafocus.emergency.EMERGENCY_UNLOCK_DECLARATION_V1
import jp.kawai.ultrafocus.emergency.EmergencyUnlockStateStore
import jp.kawai.ultrafocus.ui.emergency.EmergencyUnlockViewModel
import jp.kawai.ultrafocus.ui.emergency.NoPasteEditText
import jp.kawai.ultrafocus.ui.theme.glass
import jp.kawai.ultrafocus.ui.theme.radius
import jp.kawai.ultrafocus.ui.theme.spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EmergencyUnlockScreen(
    onBackToLock: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmergencyUnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler {
        EmergencyUnlockStateStore.setActive(context, false)
        viewModel.resetInput()
        onBackToLock()
    }

    LaunchedEffect(viewModel) {
        viewModel.unlockEvents.collectLatest {
            Toast.makeText(
                context,
                context.getString(R.string.emergency_unlock_success_toast),
                Toast.LENGTH_LONG
            ).show()
            onUnlocked()
        }
    }

    EmergencyUnlockContent(
        inputText = uiState.inputText,
        isMatched = uiState.isMatched,
        mismatchIndex = uiState.mismatchIndex,
        onInputChange = viewModel::updateInput,
        onUnlock = viewModel::requestUnlock,
        onBackToLock = {
            EmergencyUnlockStateStore.setActive(context, false)
            viewModel.resetInput()
            onBackToLock()
        },
        modifier = modifier
    )
}

@Composable
private fun EmergencyUnlockContent(
    inputText: String,
    isMatched: Boolean,
    mismatchIndex: Int?,
    onInputChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onBackToLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val scrollState = rememberScrollState()
    val declarationText = EMERGENCY_UNLOCK_DECLARATION_V1
    val targetLength = declarationText.length
    val textColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.glass.border
    val backgroundColor = MaterialTheme.glass.background
    val surfaceColor = MaterialTheme.glass.tint
    val shape = RoundedCornerShape(MaterialTheme.radius.l)
    val density = LocalDensity.current
    val inputPadding = with(density) { spacing.md.toPx().roundToInt() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = spacing.lg, vertical = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        Text(
            text = stringResource(id = R.string.emergency_unlock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(id = R.string.emergency_unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .background(backgroundColor, shape = shape)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(spacing.lg)
        ) {
            Text(
                text = declarationText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor, shape = shape)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(id = R.string.emergency_unlock_input_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            AndroidView(
                factory = { context ->
                    NoPasteEditText(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setTextColor(textColor.toArgb())
                        setHintTextColor(textColor.copy(alpha = 0.45f).toArgb())
                        textSize = 16f
                        minLines = 4
                        maxLines = 8
                        gravity = Gravity.TOP or Gravity.START
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                        hint = context.getString(R.string.emergency_unlock_input_placeholder)
                        setPadding(inputPadding, inputPadding, inputPadding, inputPadding)
                        background = null
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int
                            ) = Unit

                            override fun afterTextChanged(s: Editable?) {
                                onInputChange(s?.toString().orEmpty())
                            }
                        })
                        importantForAutofill = TextView.IMPORTANT_FOR_AUTOFILL_NO
                    }
                },
                update = { view ->
                    val current = view.text?.toString().orEmpty()
                    if (current != inputText) {
                        view.setText(inputText)
                        view.setSelection(inputText.length)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        id = R.string.emergency_unlock_input_progress,
                        inputText.length,
                        targetLength
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mismatchIndex != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(MaterialTheme.radius.s)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(MaterialTheme.radius.s)
                            )
                            .padding(horizontal = spacing.sm, vertical = spacing.xs)
                    ) {
                        Text(
                            text = "!" + stringResource(
                                id = R.string.emergency_unlock_mismatch_index,
                                mismatchIndex + 1
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (!isMatched) {
                Text(
                    text = stringResource(id = R.string.emergency_unlock_mismatch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onUnlock,
                enabled = isMatched,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(id = R.string.emergency_unlock_button),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                )
            }
            OutlinedButton(
                onClick = onBackToLock,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(id = R.string.emergency_unlock_back_button),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                )
            }
        }
    }
}
