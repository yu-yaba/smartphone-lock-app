package com.example.smartphone_lock.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

@Singleton
class DataStoreManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val SELECTED_DURATION_HOURS = intPreferencesKey("selected_duration_hours")
        val SELECTED_DURATION_MINUTES = intPreferencesKey("selected_duration_minutes")
        val LOCK_START_TIMESTAMP = longPreferencesKey("lock_start_timestamp")
        val LOCK_END_TIMESTAMP = longPreferencesKey("lock_end_timestamp")
        val IS_LOCKED = booleanPreferencesKey("is_locked")
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

    suspend fun clearLockState() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.IS_LOCKED)
            preferences.remove(Keys.LOCK_START_TIMESTAMP)
            preferences.remove(Keys.LOCK_END_TIMESTAMP)
        }
    }

    val lockState: Flow<LockStatePreferences> = preferencesFlow.map { preferences ->
        LockStatePreferences(
            isLocked = preferences[Keys.IS_LOCKED] ?: false,
            lockStartTimestamp = preferences[Keys.LOCK_START_TIMESTAMP],
            lockEndTimestamp = preferences[Keys.LOCK_END_TIMESTAMP]
        )
    }

    companion object {
        const val DEFAULT_DURATION_HOURS = 1
        const val DEFAULT_DURATION_MINUTES = 0
    }
}

data class LockStatePreferences(
    val isLocked: Boolean,
    val lockStartTimestamp: Long?,
    val lockEndTimestamp: Long?
)
