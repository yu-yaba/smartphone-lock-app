package jp.kawai.ultrafocus.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import kotlin.math.abs

/**
 * 許可アプリ起動の直後に一時的に許可パッケージを保持する。
 */
object AllowedAppLaunchStore {
    private const val PREFS_NAME = "allowed_app_launch"
    private const val KEY_PACKAGE = "package"
    private const val KEY_EXPIRES_AT = "expires_at_elapsed"
    private const val KEY_BOOT_TIME = "boot_time_epoch"
    private const val DEFAULT_TTL_MILLIS = 120_000L
    private const val KEY_LAUNCH_EXPIRES_AT = "launch_expires_at_elapsed"
    private const val DEFAULT_LAUNCH_TTL_MILLIS = 10_000L
    private const val KEY_SESSION_EXPIRES_AT = "session_expires_at_elapsed"
    private const val DEFAULT_SESSION_TTL_MILLIS = 10_000L
    private const val BOOT_TIME_TOLERANCE_MILLIS = 5_000L

    @Volatile
    private var cachedPackage: String? = null
    @Volatile
    private var cachedExpiresAt: Long = 0L
    @Volatile
    private var cachedLaunchExpiresAt: Long = 0L
    @Volatile
    private var cachedSessionExpiresAt: Long = 0L
    @Volatile
    private var cachedBootTimeEpoch: Long = 0L

    fun setAllowed(context: Context, packageName: String, ttlMillis: Long = DEFAULT_TTL_MILLIS) {
        val now = SystemClock.elapsedRealtime()
        val bootTimeEpoch = currentBootTimeEpoch()
        cachedPackage = packageName
        cachedExpiresAt = now + ttlMillis
        cachedBootTimeEpoch = bootTimeEpoch
        prefs(context).edit()
            .putLong(KEY_BOOT_TIME, bootTimeEpoch)
            .putString(KEY_PACKAGE, packageName)
            .putLong(KEY_EXPIRES_AT, now + ttlMillis)
            .apply()
    }

    fun isAllowed(context: Context, packageName: String): Boolean {
        if (!ensureBootTime(context)) {
            return false
        }
        val now = SystemClock.elapsedRealtime()
        val cached = cachedPackage
        if (!cached.isNullOrBlank() && cachedExpiresAt > now && cached == packageName) {
            return true
        }
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_PACKAGE, null) ?: return false
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L || now > expiresAt) {
            clear(context)
            return false
        }
        cachedPackage = stored
        cachedExpiresAt = expiresAt
        return stored == packageName
    }

    fun startLaunch(context: Context, ttlMillis: Long = DEFAULT_LAUNCH_TTL_MILLIS) {
        val now = SystemClock.elapsedRealtime()
        val bootTimeEpoch = currentBootTimeEpoch()
        cachedLaunchExpiresAt = now + ttlMillis
        cachedBootTimeEpoch = bootTimeEpoch
        prefs(context).edit()
            .putLong(KEY_BOOT_TIME, bootTimeEpoch)
            .putLong(KEY_LAUNCH_EXPIRES_AT, now + ttlMillis)
            .commit()
    }

    fun isLaunchInProgress(context: Context): Boolean {
        if (!ensureBootTime(context)) {
            return false
        }
        val now = SystemClock.elapsedRealtime()
        val cached = cachedLaunchExpiresAt
        if (cached > now) {
            return true
        }
        cachedLaunchExpiresAt = 0L
        val prefs = prefs(context)
        val expiresAt = prefs.getLong(KEY_LAUNCH_EXPIRES_AT, 0L)
        if (expiresAt <= 0L || now > expiresAt) {
            prefs.edit().remove(KEY_LAUNCH_EXPIRES_AT).apply()
            return false
        }
        cachedLaunchExpiresAt = expiresAt
        return true
    }

    fun clearLaunch(context: Context) {
        cachedLaunchExpiresAt = 0L
        prefs(context).edit()
            .remove(KEY_LAUNCH_EXPIRES_AT)
            .apply()
    }

    fun startSession(context: Context, ttlMillis: Long = DEFAULT_SESSION_TTL_MILLIS) {
        val now = SystemClock.elapsedRealtime()
        val bootTimeEpoch = currentBootTimeEpoch()
        cachedSessionExpiresAt = now + ttlMillis
        cachedBootTimeEpoch = bootTimeEpoch
        prefs(context).edit()
            .putLong(KEY_BOOT_TIME, bootTimeEpoch)
            .putLong(KEY_SESSION_EXPIRES_AT, now + ttlMillis)
            .commit()
    }

    fun extendSession(context: Context, ttlMillis: Long = DEFAULT_SESSION_TTL_MILLIS) {
        startSession(context, ttlMillis)
    }

    fun isSessionActive(context: Context): Boolean {
        if (!ensureBootTime(context)) {
            return false
        }
        val now = SystemClock.elapsedRealtime()
        val cached = cachedSessionExpiresAt
        if (cached > now) {
            return true
        }
        cachedSessionExpiresAt = 0L
        val prefs = prefs(context)
        val expiresAt = prefs.getLong(KEY_SESSION_EXPIRES_AT, 0L)
        if (expiresAt <= 0L || now > expiresAt) {
            prefs.edit().remove(KEY_SESSION_EXPIRES_AT).apply()
            return false
        }
        cachedSessionExpiresAt = expiresAt
        return true
    }

    fun clearSession(context: Context) {
        cachedSessionExpiresAt = 0L
        prefs(context).edit()
            .remove(KEY_SESSION_EXPIRES_AT)
            .apply()
    }

    fun clear(context: Context) {
        cachedPackage = null
        cachedExpiresAt = 0L
        cachedLaunchExpiresAt = 0L
        cachedSessionExpiresAt = 0L
        cachedBootTimeEpoch = 0L
        prefs(context).edit()
            .remove(KEY_PACKAGE)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_LAUNCH_EXPIRES_AT)
            .remove(KEY_SESSION_EXPIRES_AT)
            .remove(KEY_BOOT_TIME)
            .apply()
    }

    private fun ensureBootTime(context: Context): Boolean {
        val now = currentBootTimeEpoch()
        val cached = cachedBootTimeEpoch
        if (cached != 0L && abs(now - cached) <= BOOT_TIME_TOLERANCE_MILLIS) {
            return true
        }
        val prefs = prefs(context)
        val stored = prefs.getLong(KEY_BOOT_TIME, 0L)
        if (stored == 0L) {
            cachedBootTimeEpoch = now
            prefs.edit().putLong(KEY_BOOT_TIME, now).apply()
            return true
        }
        if (abs(now - stored) > BOOT_TIME_TOLERANCE_MILLIS) {
            clear(context)
            cachedBootTimeEpoch = now
            prefs(context).edit().putLong(KEY_BOOT_TIME, now).apply()
            return false
        }
        cachedBootTimeEpoch = stored
        return true
    }

    private fun currentBootTimeEpoch(): Long {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
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
