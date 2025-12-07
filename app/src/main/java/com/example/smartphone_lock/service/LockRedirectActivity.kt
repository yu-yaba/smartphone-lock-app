package com.example.smartphone_lock.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartphone_lock.data.repository.LockRepository
import com.example.smartphone_lock.ui.theme.SmartphoneLockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 設定アプリ等への遷移を検知した際に前面を占有し続けるブロック画面。
 * ロック解除まで閉じず、戻る操作も無効化する。
 */
@AndroidEntryPoint
class LockRedirectActivity : ComponentActivity() {

    @Inject
    lateinit var lockRepository: LockRepository

    @Inject
    lateinit var lockUiLauncher: LockUiLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // オーバーレイを前面に戻し、即終了（画面は表示しない想定）
        OverlayLockService.start(this, reason = "redirect_activity", bypassDebounce = true)
        LockMonitorService.start(this, reason = "redirect_activity", bypassDebounce = true)
        finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }
}
