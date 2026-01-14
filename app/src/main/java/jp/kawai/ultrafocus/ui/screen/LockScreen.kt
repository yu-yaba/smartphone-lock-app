package jp.kawai.ultrafocus.ui.screen

import android.app.Activity
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.kawai.ultrafocus.R
import jp.kawai.ultrafocus.model.LockPermissionState
import jp.kawai.ultrafocus.ui.lock.LockScreenUiState
import jp.kawai.ultrafocus.ui.lock.LockScreenViewModel
import jp.kawai.ultrafocus.ui.theme.UltraFocusTheme
import jp.kawai.ultrafocus.ui.theme.elevations
import jp.kawai.ultrafocus.ui.theme.glass
import jp.kawai.ultrafocus.ui.theme.radius
import jp.kawai.ultrafocus.ui.theme.spacing
import jp.kawai.ultrafocus.util.formatLockRemainingTime
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    lockViewModel: LockScreenViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissionState by lockViewModel.permissionState.collectAsStateWithLifecycle()
    val uiState by lockViewModel.uiState.collectAsStateWithLifecycle()
    LockScreenContent(
        uiState = uiState,
        permissionState = permissionState,
        onStartLock = { lockViewModel.startLock() },
        onDurationChange = lockViewModel::updateSelectedDuration,
        modifier = modifier
    )
}

@Composable
fun LockScreenContent(
    uiState: LockScreenUiState,
    permissionState: LockPermissionState,
    onStartLock: () -> Unit,
    onDurationChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val contentMaxWidth = 520.dp
    val permissionsGranted = permissionState.allGranted
    var showStartConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = spacing.lg, vertical = spacing.xxl)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            if (showStartConfirm) {
                StartLockConfirmDialog(
                    onConfirm = {
                        showStartConfirm = false
                        onStartLock()
                    },
                    onDismiss = { showStartConfirm = false }
                )
            }
            val title = stringResource(id = R.string.lock_screen_title)
            val subtitle = stringResource(id = R.string.lock_screen_subtitle)

            if (title.isNotBlank() || subtitle.isNotBlank()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentMaxWidth)
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        if (title.isNotBlank()) {
                            Spacer(modifier = Modifier.height(spacing.sm))
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (uiState.isLocked) {
                LockStatusRow(
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentMaxWidth)
                )
            }

            // Push the dial card downward so it sits slightly above center
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .padding(top = spacing.sm, bottom = spacing.sm)
            ) {
                val shape = RoundedCornerShape(MaterialTheme.radius.l)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .sizeIn(maxWidth = 460.dp)
                        .align(Alignment.Center)
                        .shadow(
                            elevation = MaterialTheme.elevations.level2,
                            shape = shape,
                            clip = true
                        )
                        .background(
                            brush = MaterialTheme.glass.highlight,
                            shape = shape
                        )
                        .background(
                            color = MaterialTheme.glass.tint,
                            shape = shape
                        )
                        .background(
                            color = MaterialTheme.glass.background,
                            shape = shape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.glass.border,
                            shape = shape
                        )
                        .padding(horizontal = spacing.xl, vertical = spacing.xl)
                ) {
                    val selectedDurationText = when {
                        uiState.selectedMinutes == 0 -> stringResource(
                            id = R.string.lock_screen_selected_duration_hours_only,
                            uiState.selectedHours
                        )
                    uiState.selectedHours == 0 -> stringResource(
                        id = R.string.lock_screen_selected_duration_minutes_only,
                        uiState.selectedMinutes
                    )
                    else -> stringResource(
                        id = R.string.lock_screen_selected_duration_hours_minutes,
                        uiState.selectedHours,
                        uiState.selectedMinutes
                    )
                }
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = selectedDurationText,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(spacing.lg))
                LockDurationDial(
                    selectedHours = uiState.selectedHours,
                    selectedMinutes = uiState.selectedMinutes,
                    onSelectionChanged = onDurationChange,
                    enabled = permissionsGranted && !uiState.isLocked,
                    modifier = Modifier.fillMaxWidth()
                )
                val durationHint = stringResource(id = R.string.lock_screen_duration_hint)
                if (durationHint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(spacing.md))
                    Text(
                        text = durationHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                AnimatedVisibility(visible = !permissionsGranted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.md)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(MaterialTheme.radius.s)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(MaterialTheme.radius.s)
                            )
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            text = stringResource(id = R.string.lock_screen_permissions_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Close BoxWithConstraints scope before placing bottom button
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showStartConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .height(54.dp)
                    .shadow(
                        elevation = MaterialTheme.elevations.level2,
                        shape = RoundedCornerShape(MaterialTheme.radius.pill),
                        clip = true
                    )
                    .background(
                        brush = MaterialTheme.glass.highlight,
                        shape = RoundedCornerShape(MaterialTheme.radius.pill)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.glass.border,
                        shape = RoundedCornerShape(MaterialTheme.radius.pill)
                    ),
                shape = RoundedCornerShape(MaterialTheme.radius.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = MaterialTheme.elevations.level1,
                    disabledElevation = 0.dp
                ),
                enabled = permissionsGranted && !uiState.isLocked
            ) {
                Text(
                    text = stringResource(id = R.string.lock_screen_start_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun LockStatusRow(
    uiState: LockScreenUiState,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing

    if (uiState.isLocked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusPill(
                text = stringResource(id = R.string.lock_screen_status_locked),
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
            text = stringResource(id = R.string.lock_screen_remaining_label),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = formatRemainingTime(uiState.remainingMillis),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        }
    }
}


@Composable
private fun StatusPill(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    Box(
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(MaterialTheme.radius.s))
            .padding(horizontal = spacing.md, vertical = spacing.xs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

private enum class DialType { HOURS, MINUTES }

private enum class MinuteCategory { MIN, STANDARD, MAX }

private fun allowedMinutesForHours(hours: Int): List<Int> {
    val increment = LockScreenViewModel.MINUTE_INCREMENT
    return when {
        hours >= LockScreenViewModel.MAX_DURATION_HOURS -> listOf(0)
        hours <= LockScreenViewModel.MIN_DURATION_HOURS -> (increment..59 step increment).toList()
        else -> (0..59 step increment).toList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockDurationDial(
    selectedHours: Int,
    selectedMinutes: Int,
    onSelectionChanged: (Int, Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val hourValues = remember { (LockScreenViewModel.MIN_DURATION_HOURS..LockScreenViewModel.MAX_DURATION_HOURS).toList() }
    val minuteFullList = remember { (0..59 step LockScreenViewModel.MINUTE_INCREMENT).toList() }
    val minuteMinList = remember { (LockScreenViewModel.MINUTE_INCREMENT..59 step LockScreenViewModel.MINUTE_INCREMENT).toList() }
    val minuteZeroList = remember { listOf(0) }

    val minuteCategory = when {
        selectedHours >= LockScreenViewModel.MAX_DURATION_HOURS -> MinuteCategory.MAX
        selectedHours <= LockScreenViewModel.MIN_DURATION_HOURS -> MinuteCategory.MIN
        else -> MinuteCategory.STANDARD
    }

    val minuteValues = remember(minuteCategory) {
        when (minuteCategory) {
            MinuteCategory.MAX -> minuteZeroList
            MinuteCategory.MIN -> minuteMinList
            MinuteCategory.STANDARD -> minuteFullList
        }
    }
    val highlightColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val dialHeight = 190.dp
    val highlightHeight = 52.dp

    val hourListState = rememberLazyListState()
    val minuteListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val sanitizedMinutes = remember(selectedHours, selectedMinutes, minuteValues) {
        minuteValues.firstOrNull { it == selectedMinutes } ?: minuteValues.firstOrNull() ?: 0
    }

    LaunchedEffect(selectedHours, sanitizedMinutes) {
        if (sanitizedMinutes != selectedMinutes) {
            onSelectionChanged(selectedHours, sanitizedMinutes)
        }
    }

    var activeDial by remember { mutableStateOf<DialType?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            activeDial = null
        }
    }

    val handleScrollStateChange: (DialType, Boolean) -> Unit = { dial, isScrolling ->
        if (isScrolling) {
            if (activeDial != dial) {
                when (dial) {
                    DialType.HOURS -> coroutineScope.launch { minuteListState.stopScroll() }
                    DialType.MINUTES -> coroutineScope.launch { hourListState.stopScroll() }
                }
            }
            activeDial = dial
        } else if (activeDial == dial) {
            activeDial = null
        }
    }

    val handlePointerDown: (DialType) -> Unit = { dial ->
        if (enabled) {
            when (dial) {
                DialType.HOURS -> coroutineScope.launch { minuteListState.stopScroll() }
                DialType.MINUTES -> coroutineScope.launch { hourListState.stopScroll() }
            }
            activeDial = dial
        }
    }

    val handleValueSelected: (DialType, Int) -> Unit = { dial, value ->
        val currentActive = activeDial
        if (currentActive == null || currentActive == dial) {
            when (dial) {
                DialType.HOURS -> {
                    val allowedMinutes = allowedMinutesForHours(value)
                    val adjustedMinutes = if (allowedMinutes.contains(sanitizedMinutes)) {
                        sanitizedMinutes
                    } else {
                        allowedMinutes.firstOrNull() ?: 0
                    }
                    onSelectionChanged(value, adjustedMinutes)
                }
                DialType.MINUTES -> {
                    onSelectionChanged(selectedHours, value)
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DialColumn(
            dialType = DialType.HOURS,
            label = stringResource(id = R.string.lock_screen_hours_label),
            values = hourValues,
            selectedValue = selectedHours,
            enabled = enabled,
            dialHeight = dialHeight,
            highlightHeight = highlightHeight,
            highlightColor = highlightColor,
            textColor = textColor,
            valueFormatter = { it.toString() },
            onScrollStateChange = handleScrollStateChange,
            onValueSelected = handleValueSelected,
            listState = hourListState,
            onPointerDown = handlePointerDown
        )
        Text(
            text = stringResource(id = R.string.lock_screen_duration_separator),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
            color = textColor.copy(alpha = 0.45f)
        )
        DialColumn(
            dialType = DialType.MINUTES,
            label = stringResource(id = R.string.lock_screen_minutes_label),
            values = minuteValues,
            selectedValue = sanitizedMinutes,
            enabled = enabled,
            dialHeight = dialHeight,
            highlightHeight = highlightHeight,
            highlightColor = highlightColor,
            textColor = textColor,
            valueFormatter = { value -> value.toString().padStart(2, '0') },
            onScrollStateChange = handleScrollStateChange,
            onValueSelected = handleValueSelected,
            listState = minuteListState,
            onPointerDown = handlePointerDown
        )
    }
}

@Composable
private fun DialColumn(
    dialType: DialType,
    label: String,
    values: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    dialHeight: Dp,
    highlightHeight: Dp,
    highlightColor: Color,
    textColor: Color,
    valueFormatter: (Int) -> String,
    onScrollStateChange: (DialType, Boolean) -> Unit,
    onValueSelected: (DialType, Int) -> Unit,
    listState: LazyListState,
    onPointerDown: (DialType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NumberDial(
            dialType = dialType,
            values = values,
            selectedValue = selectedValue,
            enabled = enabled,
            dialHeight = dialHeight,
            highlightHeight = highlightHeight,
            highlightColor = highlightColor,
            textColor = textColor,
            valueFormatter = valueFormatter,
            onScrollStateChange = onScrollStateChange,
            onValueSelected = onValueSelected,
            listState = listState,
            onPointerDown = onPointerDown
        )
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberDial(
    dialType: DialType,
    values: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    dialHeight: Dp,
    highlightHeight: Dp,
    highlightColor: Color,
    textColor: Color,
    valueFormatter: (Int) -> String,
    onScrollStateChange: (DialType, Boolean) -> Unit,
    onValueSelected: (DialType, Int) -> Unit,
    listState: LazyListState,
    onPointerDown: (DialType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        return
    }
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(values, selectedValue) {
        if (values.isEmpty()) return@LaunchedEffect
        val index = values.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
        val boundedIndex = index.coerceIn(0, values.lastIndex)
        programmaticScroll = true
        try {
            if (listState.isScrollInProgress) {
                listState.animateScrollToItem(boundedIndex)
            } else {
                listState.scrollToItem(boundedIndex)
            }
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState, enabled) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                when {
                    !enabled -> onScrollStateChange(dialType, false)
                    programmaticScroll -> if (!isScrolling) onScrollStateChange(dialType, false)
                    else -> onScrollStateChange(dialType, isScrolling)
                }
            }
    }

    LaunchedEffect(listState, enabled, values, selectedValue) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                -1
            } else {
                val viewportStart = listState.layoutInfo.viewportStartOffset
                val viewportEnd = listState.layoutInfo.viewportEndOffset
                val viewportCenter = (viewportStart + viewportEnd) / 2f
                visibleItems.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2f
                    abs(itemCenter - viewportCenter)
                }?.index ?: -1
            }
        }
            .filter { it >= 0 && it < values.size }
            .distinctUntilChanged()
            .collect { index ->
                if (!programmaticScroll) {
                    val newValue = values[index]
                    if (newValue != selectedValue) {
                        onValueSelected(dialType, newValue)
                    }
                }
            }
    }

    val edgePadding = (dialHeight - highlightHeight) / 2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dialHeight)
            .pointerInteropFilter { event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onPointerDown(dialType)
                }
                false
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(highlightHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(MaterialTheme.radius.m)
                )
        )

        LazyColumn(
            state = listState,
            userScrollEnabled = enabled,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = edgePadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { _, value ->
                val isSelected = value == selectedValue
                val targetAlpha = if (isSelected) 1f else 0.35f
                val targetScale = if (isSelected) 1.18f else 1f
                val alpha by animateFloatAsState(targetValue = targetAlpha, label = "dialAlpha")
                val scale by animateFloatAsState(targetValue = targetScale, label = "dialScale")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(highlightHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = valueFormatter(value),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        color = if (isSelected) highlightColor else textColor.copy(alpha = alpha),
                        modifier = Modifier.scale(scale)
                    )
                }
            }
        }
    }
}

@Composable
private fun StartLockConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(text = stringResource(id = R.string.lock_screen_confirm_title)) },
        text = { Text(text = stringResource(id = R.string.lock_screen_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = stringResource(id = R.string.lock_screen_confirm_positive))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = stringResource(id = R.string.lock_screen_confirm_negative))
            }
        }
    )
}

@Composable
private fun formatRemainingTime(remainingMillis: Long): String {
    val context = LocalContext.current
    return remember(remainingMillis, context) {
        formatLockRemainingTime(context, remainingMillis)
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenPreview() {
    UltraFocusTheme {
        LockScreenContent(
            uiState = LockScreenUiState(
                selectedHours = 1,
                selectedMinutes = 30,
                isLocked = false,
                remainingMillis = 90 * 60 * 1000L
            ),
            permissionState = LockPermissionState(
                overlayGranted = true,
                usageStatsGranted = true,
                exactAlarmGranted = true,
                notificationGranted = true
            ),
            onStartLock = {},
            onDurationChange = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LockScreenMissingPermissionPreview() {
    UltraFocusTheme {
        LockScreenContent(
            uiState = LockScreenUiState(
                selectedHours = 0,
                selectedMinutes = 45,
                isLocked = false,
                remainingMillis = 0
            ),
            permissionState = LockPermissionState(
                overlayGranted = false,
                usageStatsGranted = true,
                exactAlarmGranted = true,
                notificationGranted = true
            ),
            onStartLock = {},
            onDurationChange = { _, _ -> }
        )
    }
}
