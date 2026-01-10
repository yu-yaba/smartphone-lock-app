package jp.kawai.ultrafocus.emergency

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock

/**
 * 緊急解除がアクティブかどうかを永続化して保持する。
 */
object EmergencyUnlockStateStore {
    private const val PREFS_NAME = "emergency_unlock_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STARTED_AT = "started_at_elapsed"
    private const val MAX_ACTIVE_AGE_MILLIS = 10 * 60 * 1000L

    fun setActive(context: Context, active: Boolean) {
        val prefs = prefs(context)
        prefs.edit()
            .putBoolean(KEY_ACTIVE, active)
            .putLong(KEY_STARTED_AT, if (active) SystemClock.elapsedRealtime() else 0L)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return false
        val age = SystemClock.elapsedRealtime() - startedAt
        if (age < 0L || age > MAX_ACTIVE_AGE_MILLIS) {
            prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
            return false
        }
        return true
    }

    private fun prefs(context: Context): SharedPreferences {
        val baseContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        return baseContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
