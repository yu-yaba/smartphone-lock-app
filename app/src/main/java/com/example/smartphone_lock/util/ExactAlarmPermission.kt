package com.example.smartphone_lock.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Exact alarm 権限周りの共通ユーティリティ。
 */
fun AlarmManager.canUseExactAlarms(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
}

fun Context.canUseExactAlarms(): Boolean {
    val alarmManager = getSystemService(AlarmManager::class.java)
    return alarmManager?.canUseExactAlarms() ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
}

fun Context.requestExactAlarmIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }

fun Context.appDetailsSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
