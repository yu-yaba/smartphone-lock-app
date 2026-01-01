package jp.kawai.ultrafocus.data.repository

import jp.kawai.ultrafocus.model.LockPermissionState
import kotlinx.coroutines.flow.Flow

/**
 * ロック実行に必要な権限状態を提供するためのリポジトリ。
 * 現時点ではスタブ実装だが、今後オーバーレイ／使用状況／通知アクセスの状態を反映できるようにする。
 */
interface LockPermissionsRepository {
    val permissionStateFlow: Flow<LockPermissionState>

    suspend fun refreshPermissionState()
}
