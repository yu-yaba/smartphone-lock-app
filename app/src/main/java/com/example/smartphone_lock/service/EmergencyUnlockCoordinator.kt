package com.example.smartphone_lock.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 緊急解除フロー中はオーバーレイ/ロックUIの再掲出を抑制するためのグローバルフラグ。
 */
object EmergencyUnlockCoordinator {
    private val inProgressFlag = AtomicBoolean(false)

    fun start() {
        inProgressFlag.set(true)
    }

    fun finish() {
        inProgressFlag.set(false)
    }

    fun isInProgress(): Boolean = inProgressFlag.get()
}
