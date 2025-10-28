package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.config.AppConfig
import com.example.smartphone_lock.config.AppConfigProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppConfigProvider] を通じてアプリ設定を提供する標準実装。
 */
@Singleton
class DefaultConfigRepository @Inject constructor(
    private val appConfigProvider: AppConfigProvider
) : ConfigRepository {
    override fun getAppConfig(): AppConfig = appConfigProvider.appConfig
}
