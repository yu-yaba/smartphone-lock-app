package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.model.LockPermissionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 後続で権限チェックを組み込むためのスタブ実装。
 */
@Singleton
class DefaultLockPermissionsRepository @Inject constructor() : LockPermissionsRepository {

    private val state = MutableStateFlow(LockPermissionState())

    override val permissionStateFlow: Flow<LockPermissionState> = state.asStateFlow()

    override suspend fun refreshPermissionState() {
        // TODO: 実際の権限状態を取得する処理を追加する
        state.emit(state.value)
    }
}
