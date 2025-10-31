package com.example.smartphone_lock.config

import com.example.smartphone_lock.BuildConfig
import javax.inject.Inject

/**
 * BuildConfig の値を読み出して [AppConfig] を構築するデフォルト実装。
 * Supabase の接続情報は BuildConfig フィールドから直接取得し、
 * 空文字列の場合は null として扱う。
 */
class BuildConfigAppConfigProvider @Inject constructor() : AppConfigProvider {

    override val appConfig: AppConfig by lazy {
        AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() },
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() },
            supabaseServiceRoleKey = BuildConfig.SUPABASE_SERVICE_ROLE_KEY.takeIf { it.isNotBlank() },
            extras = readExtras()
        )
    }

    private fun readExtras(): Map<String, String> {
        return BuildConfig::class.java.fields
            .filter { field ->
                field.type == String::class.java && field.name.startsWith(CONFIG_EXTRA_PREFIX)
            }
            .mapNotNull { field ->
                val value = runCatching { field.get(null) as? String }.getOrNull()
                val key = field.name.removePrefix(CONFIG_EXTRA_PREFIX)
                if (value.isNullOrBlank()) {
                    null
                } else {
                    key to value
                }
            }
            .toMap()
    }

    private companion object {
        const val CONFIG_EXTRA_PREFIX = "CONFIG_EXTRA_"
    }
}
