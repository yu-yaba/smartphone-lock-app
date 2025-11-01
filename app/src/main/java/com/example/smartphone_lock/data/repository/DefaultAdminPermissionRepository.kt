package com.example.smartphone_lock.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAdminPermissionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AdminPermissionRepository {

    override val isAdminGrantedFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[KEY_IS_ADMIN_GRANTED] ?: false
        }

    override suspend fun setAdminGranted(granted: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_ADMIN_GRANTED] = granted
        }
    }

    private companion object {
        val KEY_IS_ADMIN_GRANTED = booleanPreferencesKey("is_admin_granted")
    }
}
