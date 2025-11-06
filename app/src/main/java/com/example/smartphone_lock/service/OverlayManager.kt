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

    open fun show() {
        Log.d(TAG, "Requesting overlay display")
        LockMonitorService.start(context)
        OverlayLockService.start(context)
    }

    open fun hide() {
        Log.d(TAG, "Requesting overlay hide")
        OverlayLockService.stop(context)
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}
