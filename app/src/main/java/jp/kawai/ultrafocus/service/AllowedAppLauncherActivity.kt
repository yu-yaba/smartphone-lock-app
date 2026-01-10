package jp.kawai.ultrafocus.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import jp.kawai.ultrafocus.util.AllowedAppResolver

/**
 * 電話/SMS の既定アプリを起動するための中継アクティビティ。
 * オーバーレイからの起動がブロックされるケースに備えて前面で実行する。
 */
class AllowedAppLauncherActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handled) return
        handled = true
        val target = intent?.getStringExtra(EXTRA_TARGET)
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val launched = when (target) {
            Target.DIALER.name -> launchDialer(targetPackage)
            Target.SMS.name -> launchSms(targetPackage)
            else -> false
        }
        if (!launched) {
            AllowedAppLaunchStore.clearLaunch(this)
            AllowedAppLaunchStore.clearSession(this)
            OverlayLockService.setAllowedAppSuppressed(
                this,
                suppressed = false,
                reason = "allowed_app_launch_failed"
            )
        }
        finish()
    }

    private fun launchDialer(targetPackage: String?): Boolean {
        val primary = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
        val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
        val resolvedPackage = AllowedAppResolver.resolveDialerPackage(this, targetPackage)
        if (resolvedPackage.isNullOrBlank()) {
            Log.w(TAG, "No default dialer resolved")
            return false
        }
        primary.`package` = resolvedPackage
        fallback.`package` = resolvedPackage
        return startExternal(primary, fallback, "dialer", resolvedPackage)
    }

    private fun launchSms(targetPackage: String?): Boolean {
        val primary = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MESSAGING)
        }
        val fallback = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
        }
        val resolvedPackage = AllowedAppResolver.resolveSmsPackage(this, targetPackage)
        if (resolvedPackage.isNullOrBlank()) {
            Log.w(TAG, "No default sms app resolved")
            return false
        }
        primary.`package` = resolvedPackage
        fallback.`package` = resolvedPackage
        return startExternal(primary, fallback, "sms", resolvedPackage)
    }

    private fun startExternal(
        primary: Intent,
        fallback: Intent,
        label: String,
        resolvedPackage: String
    ): Boolean {
        if (tryStart(primary, label, resolvedPackage)) {
            return true
        }
        if (tryStart(fallback, label, resolvedPackage)) {
            return true
        }
        Log.w(TAG, "No activity found for $label")
        return false
    }

    private fun tryStart(intent: Intent, label: String, resolvedPackage: String): Boolean {
        val targetPackage = resolvedPackage
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            startActivity(intent)
        }.onSuccess {
            Log.d(TAG, "Started $label package=$targetPackage")
            AllowedAppLaunchStore.setAllowed(this, targetPackage)
            AllowedAppLaunchStore.extendSession(this, ttlMillis = 60_000L)
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to open $label", throwable)
        }.isSuccess
    }

    enum class Target {
        DIALER,
        SMS
    }

    companion object {
        private const val TAG = "AllowedAppLauncher"
        private const val EXTRA_TARGET = "extra_target"
        private const val EXTRA_TARGET_PACKAGE = "extra_target_package"

        fun start(context: Context, target: Target, targetPackage: String?) {
            val intent = Intent(context, AllowedAppLauncherActivity::class.java).apply {
                putExtra(EXTRA_TARGET, target.name)
                putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
