package com.example.smartphone_lock.service

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * ブラックリスト検知時にタスクを前面へ戻し、即座にサービスを再始動して終了する
 * 「ワンショット」アクティビティ。UIは持たず、履歴にも残さない。
 */
class LockRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 可能な限り早くフォアグラウンドを奪取し、オーバーレイ/監視を再開する
        OverlayLockService.start(this, "redirect_activity")
        LockMonitorService.start(this, "redirect_activity")
        // 即終了して履歴を残さない
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        // onCreate で finish 済みだが、念のため再度終了を試みる
        if (!isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
