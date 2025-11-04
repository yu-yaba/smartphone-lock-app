package com.example.smartphone_lock.ui.screen

import android.app.Activity
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartphone_lock.R
import com.example.smartphone_lock.ui.lock.LockScreenViewModel
import com.example.smartphone_lock.util.formatLockRemainingTime
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
    val permissionsGranted = permissionState.allGranted
    val uiState by lockViewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val columnScope = this
            Text(
                text = stringResource(id = R.string.lock_screen_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.lock_screen_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                columnScope.AnimatedVisibility(
                    visible = !uiState.isLocked,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.sizeIn(maxWidth = 480.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.lock_screen_duration_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                        Text(
                            text = selectedDurationText,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        LockDurationDial(
                            selectedHours = uiState.selectedHours,
                            selectedMinutes = uiState.selectedMinutes,
                            onSelectionChanged = lockViewModel::updateSelectedDuration,
                            enabled = permissionsGranted,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.lock_screen_duration_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        AnimatedVisibility(visible = !permissionsGranted) {
                            Text(
                                text = stringResource(id = R.string.lock_screen_permissions_missing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }

                columnScope.AnimatedVisibility(
                    visible = uiState.isLocked,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.sizeIn(maxWidth = 480.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.lock_screen_locked_message),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = formatRemainingTime(uiState.remainingMillis),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.isLocked) {
                OutlinedButton(
                    onClick = { lockViewModel.stopLock(activity) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.lock_screen_stop_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Button(
                onClick = { lockViewModel.startLock(activity) },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(32.dp), clip = false),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = activity != null && permissionsGranted && !uiState.isLocked
            ) {
                Text(
                    text = stringResource(id = R.string.lock_screen_start_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private enum class DialType { HOURS, MINUTES }

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
    val minuteValues = remember(selectedHours) { allowedMinutesForHours(selectedHours) }
    val highlightColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val dialHeight = 200.dp
    val highlightHeight = 56.dp

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
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
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
            color = textColor.copy(alpha = 0.6f)
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor.copy(alpha = 0.7f)
        )
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
                    color = highlightColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(highlightHeight / 2)
                )
                .border(
                    width = 1.dp,
                    color = highlightColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(highlightHeight / 2)
                )
        )

        LazyColumn(
            state = listState,
            userScrollEnabled = enabled,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = 64.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { _, value ->
                val isSelected = value == selectedValue
                val targetAlpha = if (isSelected) 1f else 0.3f
                val targetScale = if (isSelected) 1.25f else 1f
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
private fun formatRemainingTime(remainingMillis: Long): String {
    val context = LocalContext.current
    return remember(remainingMillis, context) {
        formatLockRemainingTime(context, remainingMillis)
    }
}
