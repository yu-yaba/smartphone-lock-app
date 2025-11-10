package com.example.smartphone_lock.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile

/**
 * ActivityManager のプロセス情報をポーリングして SystemUI が前面化したかを監視する。
 * UsageStats がイベントを出さない端末へのフォールバック経路となる。
 */
@Singleton
class SystemUiForegroundWatcher @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    @Volatile
    private var watchJobActive = false

    fun start(scope: CoroutineScope, onSystemUiForeground: (String) -> Unit) {
        val manager = activityManager ?: return
        if (watchJobActive) return
        watchJobActive = true
        val job = scope.launch(dispatcher) {
            while (isActive) {
                try {
                    if (isSystemUiLikelyForeground(manager)) {
                        onSystemUiForeground(SYSTEM_UI_PACKAGE)
                    }
                } catch (throwable: SecurityException) {
                    Log.w(TAG, "Unable to query running processes", throwable)
                } catch (throwable: Throwable) {
                    Log.e(TAG, "SystemUI watcher failed", throwable)
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        job.invokeOnCompletion { watchJobActive = false }
    }

    private fun isSystemUiLikelyForeground(manager: ActivityManager): Boolean {
        val processes = manager.runningAppProcesses ?: return false
        return processes.any { process ->
            process.processName == SYSTEM_UI_PACKAGE &&
                process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    companion object {
        private const val TAG = "SystemUiWatcher"
        private const val POLL_INTERVAL_MILLIS = 750L
        internal const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
