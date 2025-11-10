package com.example.smartphone_lock.data.repository

/**
 * 設定アプリ関連のパッケージ一覧。SystemUI やその他アプリは対象外。
 */
object SettingsPackages {
    val TARGETS: Set<String> = setOf(
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.app.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.oppo.safe",
        "com.vivo.settings",
        "com.huawei.systemmanager",
        "com.oneplus.security",
        "com.realme.securitycenter",
        "com.coloros.safecenter",
    )
}
