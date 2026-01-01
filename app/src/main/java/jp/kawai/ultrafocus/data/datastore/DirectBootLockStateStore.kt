package jp.kawai.ultrafocus.data.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class DirectBootLockStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val deviceProtectedContext = context.createDeviceProtectedStorageContext()
    private val sharedPreferences: SharedPreferences =
        deviceProtectedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val stateFlow = MutableStateFlow(readFromPreferences())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        stateFlow.value = readFromPreferences()
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun flow(): Flow<DeviceProtectedLockStatePreferences> = stateFlow

    fun snapshot(): DeviceProtectedLockStatePreferences = stateFlow.value

    fun write(
        isLocked: Boolean,
        lockStartTimestamp: Long?,
        lockEndTimestamp: Long?
    ) {
        val editor = sharedPreferences.edit()
            .putBoolean(KEY_IS_LOCKED, isLocked)
        if (lockStartTimestamp != null) {
            editor.putLong(KEY_LOCK_START_TIMESTAMP, lockStartTimestamp)
        } else {
            editor.remove(KEY_LOCK_START_TIMESTAMP)
        }
        if (lockEndTimestamp != null) {
            editor.putLong(KEY_LOCK_END_TIMESTAMP, lockEndTimestamp)
        } else {
            editor.remove(KEY_LOCK_END_TIMESTAMP)
        }

        val success = editor.commit()
        if (!success) {
            Log.w(TAG, "Failed to persist direct-boot lock state")
        }
        stateFlow.value = readFromPreferences()
    }

    fun clear() {
        val success = sharedPreferences.edit()
            .remove(KEY_IS_LOCKED)
            .remove(KEY_LOCK_START_TIMESTAMP)
            .remove(KEY_LOCK_END_TIMESTAMP)
            .commit()
        if (!success) {
            Log.w(TAG, "Failed to clear direct-boot lock state")
        }
        stateFlow.value = readFromPreferences()
    }

    private fun readFromPreferences(): DeviceProtectedLockStatePreferences {
        val hasStart = sharedPreferences.contains(KEY_LOCK_START_TIMESTAMP)
        val hasEnd = sharedPreferences.contains(KEY_LOCK_END_TIMESTAMP)
        return DeviceProtectedLockStatePreferences(
            isLocked = sharedPreferences.getBoolean(KEY_IS_LOCKED, false),
            lockStartTimestamp = if (hasStart) sharedPreferences.getLong(KEY_LOCK_START_TIMESTAMP, 0L) else null,
            lockEndTimestamp = if (hasEnd) sharedPreferences.getLong(KEY_LOCK_END_TIMESTAMP, 0L) else null
        )
    }

    private companion object {
        private const val TAG = "DirectBootLockStore"
        private const val PREFS_NAME = "direct_boot_lock_state"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_START_TIMESTAMP = "lock_start_timestamp"
        private const val KEY_LOCK_END_TIMESTAMP = "lock_end_timestamp"
    }
}
