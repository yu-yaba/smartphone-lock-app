package jp.kawai.ultrafocus.config

/**
 * Supabase 接続に必要な設定値をまとめたデータクラス。
 */
data class SupabaseConfig(
    val url: String?,
    val anonKey: String?
)
