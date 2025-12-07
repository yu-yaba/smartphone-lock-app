package com.example.smartphone_lock.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Overlay 表示の制御を一箇所に集約するヘルパー。
 */
@Singleton
open class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    open fun show(bypassDebounce: Boolean = false) {
        if (EmergencyUnlockCoordinator.isInProgress()) {
            Log.d(TAG, "Overlay suppressed: emergency unlock in progress")
            return
        }
        Log.d(TAG, "Requesting overlay display")
        LockMonitorService.start(context, bypassDebounce = bypassDebounce)
        OverlayLockService.start(context, bypassDebounce = bypassDebounce)
    }

    open fun hide() {
        Log.d(TAG, "Requesting overlay hide")
        OverlayLockService.stop(context)
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}
