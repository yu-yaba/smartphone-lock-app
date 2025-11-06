package com.example.smartphone_lock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.smartphone_lock.data.repository.LockRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 使用状況監視サービスの土台。現在はスタブ実装で、後続ブランチで
 * フォアグラウンド化や UsageStats 監視ロジックを追加する。
 */
@AndroidEntryPoint
class LockMonitorService : Service() {

    @Inject
    lateinit var lockRepository: LockRepository

    @Inject
    lateinit var foregroundAppMonitor: ForegroundAppMonitor

    @Inject
    lateinit var overlayManager: OverlayManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LockMonitorService stub started")
        // TODO: 後続ブランチでロック状態の Flow 監視と UsageWatcher 起動を実装する
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LockMonitorService"

        fun start(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
