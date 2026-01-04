package jp.kawai.ultrafocus.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.kawai.ultrafocus.MainActivity
import jp.kawai.ultrafocus.emergency.EmergencyUnlockState
import jp.kawai.ultrafocus.navigation.AppDestination
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ自身のフルスクリーン UI を最前面に呼び戻すヘルパー。
 */
@Singleton
class LockUiLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun bringToFront() {
        if (EmergencyUnlockState.isActive()) {
            Log.d(TAG, "Emergency unlock active; bring emergency screen to front")
            bringEmergencyUnlockToFront()
            return
        }
        val intent = Intent(context, LockRedirectActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Failed to bring lock UI to front", it) }
    }

    fun bringEmergencyUnlockToFront() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAV_ROUTE, AppDestination.EmergencyUnlock.route)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Failed to bring emergency unlock to front", it) }
    }

    companion object {
        private const val TAG = "LockUiLauncher"
    }
}
