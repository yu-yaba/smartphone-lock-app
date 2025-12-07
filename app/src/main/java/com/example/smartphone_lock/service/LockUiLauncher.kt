package com.example.smartphone_lock.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * オーバーレイを最前面に呼び戻すヘルパー。
 */
@Singleton
class LockUiLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlayManager: OverlayManager,
) {

    fun bringToFront() {
        runCatching { overlayManager.show(bypassDebounce = true) }
            .onFailure { Log.e(TAG, "Failed to bring overlay to front", it) }
    }

    companion object {
        private const val TAG = "LockUiLauncher"
    }
}
