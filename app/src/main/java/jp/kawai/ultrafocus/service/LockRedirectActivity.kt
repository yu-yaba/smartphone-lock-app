package jp.kawai.ultrafocus.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import jp.kawai.ultrafocus.data.repository.LockRepository
import jp.kawai.ultrafocus.ui.theme.UltraFocusTheme
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

        // 戻るキー無効化（ロック解除時にのみ閉じる）
        onBackPressedDispatcher.addCallback(this) { /* no-op while locked */ }

        // 可能な限り早くフォアグラウンドを奪取し、オーバーレイ/監視を再開する
        OverlayLockService.start(this, reason = "redirect_activity", bypassDebounce = true)
        LockMonitorService.start(this, reason = "redirect_activity", bypassDebounce = true)

        // ロック解除されたら自動で閉じる
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                lockRepository.lockState.collect { state ->
                    if (!state.isLocked) finish()
                }
            }
        }

        setContent {
            UltraFocusTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "利用を制限しています",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "ロック解除まで他の画面に戻れません",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // ホーム/タスク切替を試みた場合も前面を奪い返す
        lockUiLauncher.bringToFront()
    }
}
