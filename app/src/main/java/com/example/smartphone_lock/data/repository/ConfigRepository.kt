package com.example.smartphone_lock.data.repository

import com.example.smartphone_lock.config.AppConfig

/**
 * アプリ設定の取得を提供するリポジトリインターフェース。
 * データソースの詳細（BuildConfig、ローカル設定、リモート設定など）を隠蔽する。
 */
interface ConfigRepository {
    fun getAppConfig(): AppConfig
}
