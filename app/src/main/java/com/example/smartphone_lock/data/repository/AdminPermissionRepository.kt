package com.example.smartphone_lock.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * 永続化された管理者権限の状態を提供するリポジトリ。
 */
interface AdminPermissionRepository {
    val isAdminGrantedFlow: Flow<Boolean>

    suspend fun setAdminGranted(granted: Boolean)
}
