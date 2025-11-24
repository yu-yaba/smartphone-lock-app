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

        // Package installers
        "com.android.packageinstaller",          // AOSP installer
        "com.google.android.packageinstaller",   // GMS/Pixel installer
        "com.samsung.android.packageinstaller",  // Samsung installer
        "com.miui.packageinstaller",             // Xiaomi/MIUI installer
        "com.coloros.safecenter",                // Oppo/ColorOS installer (fallback)

        // App stores (uninstall/disable経路の封じ込み)
        "com.android.vending",                   // Google Play Store
        "com.sec.android.app.samsungapps",       // Samsung Galaxy Store
        "com.xiaomi.market",                     // Xiaomi GetApps
        "com.oppo.market",                       // Oppo Market
        "com.vivo.appstore",                     // Vivo App Store
        "com.huawei.appmarket"                   // Huawei AppGallery
    )
}
