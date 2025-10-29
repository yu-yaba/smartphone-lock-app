package com.example.smartphone_lock.config

/**
 * アプリ全体で共有する設定値をまとめたデータクラス。
 * BuildConfig やリモート設定など、取得元に依存しない形で保持する。
 */
data class AppConfig(
    val supabaseUrl: String? = null,
    val supabaseAnonKey: String? = null,
    val supabaseServiceRoleKey: String? = null,
    val extras: Map<String, String> = emptyMap()
)
