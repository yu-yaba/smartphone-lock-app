package jp.kawai.ultrafocus.data.repository

import jp.kawai.ultrafocus.data.datastore.DeviceProtectedLockStatePreferences
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
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
     * ロック中に許可する外部アプリ（電話/SMS）のパッケージ情報を返す。
     */
    fun allowedAppTargets(): AllowedAppTargets

    /**
     * 端末の既定ダイヤラ/SMS アプリなど、動的に許可すべきアプリ一覧を再評価する。
     */
    fun refreshDynamicLists()
}
