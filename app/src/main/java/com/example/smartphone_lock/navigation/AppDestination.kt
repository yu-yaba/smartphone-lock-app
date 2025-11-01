package com.example.smartphone_lock.navigation

enum class AppDestination(val route: String) {
    Permission("permission"),
    Lock("lock"),
    Home("home"),
    LockSetting("lock_setting"),
    Auth("auth"),
    Complete("complete");
}
