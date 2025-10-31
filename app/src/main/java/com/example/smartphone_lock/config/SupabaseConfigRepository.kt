package com.example.smartphone_lock.config

import com.example.smartphone_lock.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BuildConfig に埋め込まれた Supabase の設定値を提供するリポジトリ。
 * 値はログ出力せず、空文字列は null として扱う。
 */
@Singleton
class SupabaseConfigRepository @Inject constructor() {

    fun fetch(): SupabaseConfig {
        return SupabaseConfig(
            url = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() },
            anonKey = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() }
        )
    }
}
