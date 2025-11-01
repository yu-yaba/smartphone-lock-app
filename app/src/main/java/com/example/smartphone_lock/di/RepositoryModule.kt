package com.example.smartphone_lock.di

import com.example.smartphone_lock.data.repository.AdminPermissionRepository
import com.example.smartphone_lock.data.repository.ConfigRepository
import com.example.smartphone_lock.data.repository.DefaultAdminPermissionRepository
import com.example.smartphone_lock.data.repository.DefaultConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 実装のバインディングをまとめる Hilt モジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(
        impl: DefaultConfigRepository
    ): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindAdminPermissionRepository(
        impl: DefaultAdminPermissionRepository
    ): AdminPermissionRepository
}
