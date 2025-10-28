package com.example.smartphone_lock.config

/**
 * 設定値の取得手段を抽象化するインターフェース。
 * 実装は BuildConfig やリモート設定サービスなど、環境ごとに差し替え可能とする。
 */
interface AppConfigProvider {
    val appConfig: AppConfig
}
