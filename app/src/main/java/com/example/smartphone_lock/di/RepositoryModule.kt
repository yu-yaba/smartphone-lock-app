package com.example.smartphone_lock.di

import com.example.smartphone_lock.data.repository.DefaultLockPermissionsRepository
import com.example.smartphone_lock.data.repository.DefaultLockRepository
import com.example.smartphone_lock.data.repository.LockPermissionsRepository
import com.example.smartphone_lock.data.repository.LockRepository
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
    abstract fun bindLockPermissionsRepository(
        impl: DefaultLockPermissionsRepository
    ): LockPermissionsRepository

    @Binds
    @Singleton
    abstract fun bindLockRepository(
        impl: DefaultLockRepository
    ): LockRepository
}
