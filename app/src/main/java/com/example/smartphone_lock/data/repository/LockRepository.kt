package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.data.datastore.LockStatePreferences
import kotlinx.coroutines.flow.Flow

/**
 * ロック状態や封鎖対象アプリの定義を提供するリポジトリ。
 */
interface LockRepository {

    /** デバイスのロック状態。 */
    val lockState: Flow<LockStatePreferences>

    /**
     * 指定したパッケージ名を封鎖対象とするかを判定する。
     *
     * @param packageName 判定するパッケージ名
     * @return true の場合は Overlay で封鎖すべきアプリ
     */
    fun shouldBlockPackage(packageName: String): Boolean

    /**
     * ホワイトリストに登録されたパッケージ名かを返す。
     */
    fun isWhitelisted(packageName: String): Boolean

    /**
     * ブラックリスト（設定アプリ等）に該当するかを返す。
     */
    fun isBlacklisted(packageName: String): Boolean

    /**
     * 設定アプリや SystemUI など、即座に自アプリへリダイレクトすべきパッケージか。
     */
    fun shouldForceLockUi(packageName: String): Boolean

    /**
     * 端末の既定ダイヤラ/SMS アプリなど、動的に許可すべきアプリ一覧を再評価する。
     */
    fun refreshDynamicLists()
}
