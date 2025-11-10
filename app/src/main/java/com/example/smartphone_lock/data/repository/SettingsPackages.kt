package com.example.smartphone_lock.data.repository

/**
 * 主要 OEM/Google 端末で利用される設定アプリや権限コントローラのパッケージ一覧。
 * UsageStats やブラックリスト判定で複数箇所から参照できるよう単一オブジェクトに集約する。
 */
object SettingsPackages {
    private val CORE_SETTINGS = setOf(
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.app.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.vivo.settings",
        "com.huawei.systemmanager",
        "com.oneplus.security",
        "com.realme.securitycenter",
    )

    /** ブラックリスト対象の設定アプリ群 */
    val KNOWN: Set<String> = CORE_SETTINGS + SYSTEM_UI_PACKAGE

    /** 即時リダイレクトしたい優先度の高いパッケージ */
    val REDIRECT_TARGETS: Set<String> = setOf(
        SYSTEM_UI_PACKAGE,
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
