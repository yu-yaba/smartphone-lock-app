package com.example.smartphone_lock.util

import android.content.Context
import com.example.smartphone_lock.R

fun formatLockRemainingTime(context: Context, remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0)
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()

    return when {
        hours > 0 -> context.getString(
            R.string.lock_screen_remaining_time_hours_minutes,
            hours,
            minutes
        )

        minutes > 0 -> context.getString(
            R.string.lock_screen_remaining_time_minutes_seconds,
            minutes,
            seconds
        )

        else -> context.getString(
            R.string.lock_screen_remaining_time_seconds,
            seconds
        )
    }
}
