package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * ロック状態と設定アプリ検知のみを扱うシンプルな実装。
 */
@Singleton
class DefaultLockRepository @Inject constructor(
    private val dataStoreManager: DataStoreManager,
) : LockRepository {

    override val lockState: Flow<LockStatePreferences> = dataStoreManager.lockState

    override fun shouldForceLockUi(packageName: String): Boolean {
        val normalized = packageName.trim()
        return normalized.isNotEmpty() && SettingsPackages.TARGETS.contains(normalized)
    }

    override fun refreshDynamicLists() {
        // 設定アプリのみを対象とするため、動的リスト処理は不要。
    }
}
