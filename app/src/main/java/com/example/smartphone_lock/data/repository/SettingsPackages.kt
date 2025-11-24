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
        // SystemUI (Notification Shade / Quick Settings / Recents on some devices)
        "com.android.systemui",
        // Launchers (Home Screen / Recents)
        "com.google.android.apps.nexuslauncher", // Pixel
        "com.android.launcher3",                 // AOSP
        "com.sec.android.app.launcher",          // Samsung
        "com.miui.home",                         // Xiaomi
        "com.huawei.android.launcher",           // Huawei
        "com.oppo.launcher",                     // Oppo
        "com.vivo.launcher",                     // Vivo
        // Voice Assistants
        "com.google.android.googlequicksearchbox", // Google App / Assistant
        "com.google.android.apps.googleassistant", // Assistant standalone
        "com.samsung.android.bixby.agent",       // Bixby
        "com.amazon.dee.app",                    // Alexa
    )
}
