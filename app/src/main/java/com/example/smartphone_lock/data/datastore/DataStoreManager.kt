package com.example.smartphone_lock.data.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.smartphone_lock.di.CredentialEncryptedDataStore
import com.example.smartphone_lock.di.DeviceProtectedDataStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreManager @Inject constructor(
    @CredentialEncryptedDataStore
    private val dataStore: DataStore<Preferences>,
    @DeviceProtectedDataStore
    private val deviceProtectedDataStore: DataStore<Preferences>,
    private val directBootLockStateStore: DirectBootLockStateStore
) {

    private object Keys {
        val SELECTED_DURATION_HOURS = intPreferencesKey("selected_duration_hours")
        val SELECTED_DURATION_MINUTES = intPreferencesKey("selected_duration_minutes")
        val LOCK_START_TIMESTAMP = longPreferencesKey("lock_start_timestamp")
        val LOCK_END_TIMESTAMP = longPreferencesKey("lock_end_timestamp")
        val IS_LOCKED = booleanPreferencesKey("is_locked")
    }

    private object DeviceProtectedKeys {
        val LOCK_START_TIMESTAMP = longPreferencesKey("dp_lock_start_timestamp")
        val LOCK_END_TIMESTAMP = longPreferencesKey("dp_lock_end_timestamp")
        val IS_LOCKED = booleanPreferencesKey("dp_is_locked")
    }

    private val preferencesFlow: Flow<Preferences> = dataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    val selectedDurationHours: Flow<Int> = preferencesFlow.map { preferences ->
        preferences[Keys.SELECTED_DURATION_HOURS] ?: DEFAULT_DURATION_HOURS
    }

    val selectedDurationMinutes: Flow<Int> = preferencesFlow.map { preferences ->
        (preferences[Keys.SELECTED_DURATION_MINUTES] ?: DEFAULT_DURATION_MINUTES).coerceIn(0, 59)
    }

    val lockEndTimestamp: Flow<Long?> = preferencesFlow.map { preferences ->
        preferences[Keys.LOCK_END_TIMESTAMP]
    }

    val lockStartTimestamp: Flow<Long?> = preferencesFlow.map { preferences ->
        preferences[Keys.LOCK_START_TIMESTAMP]
    }

    val isLocked: Flow<Boolean> = preferencesFlow.map { preferences ->
        preferences[Keys.IS_LOCKED] ?: false
    }

    suspend fun updateSelectedDuration(hours: Int, minutes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_DURATION_HOURS] = hours
            preferences[Keys.SELECTED_DURATION_MINUTES] = minutes
        }
    }

    suspend fun updateLockState(
        isLocked: Boolean,
        lockStartTimestamp: Long?,
        lockEndTimestamp: Long?
    ) {
        directBootLockStateStore.write(isLocked, lockStartTimestamp, lockEndTimestamp)
        writeDeviceProtectedLockState(isLocked, lockStartTimestamp, lockEndTimestamp)
        writeCredentialEncryptedLockStateSafely(isLocked, lockStartTimestamp, lockEndTimestamp)
    }

    suspend fun syncCredentialStoreFromDeviceProtected() {
        val deviceProtectedState = directBootLockStateStore.snapshot()
        writeCredentialEncryptedLockStateSafely(
            deviceProtectedState.isLocked,
            deviceProtectedState.lockStartTimestamp,
            deviceProtectedState.lockEndTimestamp
        )
    }

    val lockState: Flow<LockStatePreferences> = preferencesFlow.map { preferences ->
        LockStatePreferences(
            isLocked = preferences[Keys.IS_LOCKED] ?: false,
            lockStartTimestamp = preferences[Keys.LOCK_START_TIMESTAMP],
            lockEndTimestamp = preferences[Keys.LOCK_END_TIMESTAMP]
        )
    }

    val deviceProtectedLockState: Flow<DeviceProtectedLockStatePreferences> = directBootLockStateStore.flow()

    fun deviceProtectedSnapshot(): DeviceProtectedLockStatePreferences = directBootLockStateStore.snapshot()

    companion object {
        const val DEFAULT_DURATION_HOURS = 1
        const val DEFAULT_DURATION_MINUTES = 0
        private const val TAG = "DataStoreManager"
    }

    private suspend fun writeCredentialEncryptedLockStateSafely(
        isLocked: Boolean,
        lockStartTimestamp: Long?,
        lockEndTimestamp: Long?
    ) {
        try {
            writeCredentialEncryptedLockState(isLocked, lockStartTimestamp, lockEndTimestamp)
        } catch (throwable: Throwable) {
            if (throwable is IllegalStateException) {
                Log.w(TAG, "Credential storage unavailable; deferring CE lock-state update", throwable)
            } else {
                throw throwable
            }
        }
    }

    private suspend fun writeCredentialEncryptedLockState(
        isLocked: Boolean,
        lockStartTimestamp: Long?,
        lockEndTimestamp: Long?
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.IS_LOCKED] = isLocked
            if (lockStartTimestamp != null) {
                preferences[Keys.LOCK_START_TIMESTAMP] = lockStartTimestamp
            } else {
                preferences.remove(Keys.LOCK_START_TIMESTAMP)
            }
            if (lockEndTimestamp != null) {
                preferences[Keys.LOCK_END_TIMESTAMP] = lockEndTimestamp
            } else {
                preferences.remove(Keys.LOCK_END_TIMESTAMP)
            }
        }
    }

    private suspend fun writeDeviceProtectedLockState(
        isLocked: Boolean,
        lockStartTimestamp: Long?,
        lockEndTimestamp: Long?
    ) {
        deviceProtectedDataStore.edit { preferences ->
            preferences[DeviceProtectedKeys.IS_LOCKED] = isLocked
            if (lockStartTimestamp != null) {
                preferences[DeviceProtectedKeys.LOCK_START_TIMESTAMP] = lockStartTimestamp
            } else {
                preferences.remove(DeviceProtectedKeys.LOCK_START_TIMESTAMP)
            }
            if (lockEndTimestamp != null) {
                preferences[DeviceProtectedKeys.LOCK_END_TIMESTAMP] = lockEndTimestamp
            } else {
                preferences.remove(DeviceProtectedKeys.LOCK_END_TIMESTAMP)
            }
        }
    }
}

data class LockStatePreferences(
    val isLocked: Boolean,
    val lockStartTimestamp: Long?,
    val lockEndTimestamp: Long?
)

data class DeviceProtectedLockStatePreferences(
    val isLocked: Boolean,
    val lockStartTimestamp: Long?,
    val lockEndTimestamp: Long?
)
