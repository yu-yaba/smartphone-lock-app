package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.data.datastore.DeviceProtectedLockStatePreferences
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import kotlinx.coroutines.flow.Flow

/**
 * ロック状態や封鎖対象アプリの定義を提供するリポジトリ。
 */
interface LockRepository {

    /** デバイスのロック状態。 */
    val lockState: Flow<LockStatePreferences>

    /** Direct Boot（DP）領域に保持しているロック状態。 */
    val deviceProtectedLockState: Flow<DeviceProtectedLockStatePreferences>

    /**
     * 設定アプリや SystemUI など、即座に自アプリへリダイレクトすべきパッケージか。
     */
    fun shouldForceLockUi(packageName: String): Boolean

    /**
     * 端末の既定ダイヤラ/SMS アプリなど、動的に許可すべきアプリ一覧を再評価する。
     */
    fun refreshDynamicLists()
}
