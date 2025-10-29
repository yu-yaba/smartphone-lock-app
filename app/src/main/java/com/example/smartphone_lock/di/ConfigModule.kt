package com.example.smartphone_lock.di

import com.example.smartphone_lock.config.AppConfig
import com.example.smartphone_lock.config.AppConfigProvider
import com.example.smartphone_lock.config.BuildConfigAppConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 設定値関連の依存解決を定義する Hilt モジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @Singleton
    fun provideAppConfigProvider(): AppConfigProvider = BuildConfigAppConfigProvider()

    @Provides
    @Singleton
    fun provideAppConfig(provider: AppConfigProvider): AppConfig = provider.appConfig
}
