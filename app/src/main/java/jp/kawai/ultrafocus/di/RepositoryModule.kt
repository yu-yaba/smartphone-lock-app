package jp.kawai.ultrafocus.di

import jp.kawai.ultrafocus.data.repository.DefaultLockPermissionsRepository
import jp.kawai.ultrafocus.data.repository.DefaultLockRepository
import jp.kawai.ultrafocus.data.repository.LockPermissionsRepository
import jp.kawai.ultrafocus.data.repository.LockRepository
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
