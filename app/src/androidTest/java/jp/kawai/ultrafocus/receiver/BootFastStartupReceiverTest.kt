package jp.kawai.ultrafocus.receiver

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.kawai.ultrafocus.service.LockMonitorService
import jp.kawai.ultrafocus.service.OverlayLockService
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootFastStartupReceiverTest {

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OverlayLockService.stop(context)
        LockMonitorService.stop(context)
        val dpContext = context.createDeviceProtectedStorageContext()
        dpContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun bootFastReceiverStartsServicesWhenLockActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dpContext = context.createDeviceProtectedStorageContext()
        val now = System.currentTimeMillis()
        val end = now + TEN_MINUTES_MILLIS
        dpContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_LOCKED, true)
            .putLong(KEY_LOCK_START_TIMESTAMP, now)
            .putLong(KEY_LOCK_END_TIMESTAMP, end)
            .commit()

        val receiver = BootFastStartupReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        SystemClock.sleep(2_000L)

        val running = runningServiceNames(context)
        val overlayRunning = running.contains(OverlayLockService::class.java.name)
        val monitorRunning = running.contains(LockMonitorService::class.java.name)
        assertTrue(
            "Expected OverlayLockService or LockMonitorService to be running, but got $running",
            overlayRunning || monitorRunning
        )
    }

    private fun runningServiceNames(context: Context): Set<String> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = am.getRunningServices(Int.MAX_VALUE)
        return services.map { it.service.className }.toSet()
    }

    private companion object {
        private const val PREFS_NAME = "direct_boot_lock_state"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_START_TIMESTAMP = "lock_start_timestamp"
        private const val KEY_LOCK_END_TIMESTAMP = "lock_end_timestamp"
        private const val TEN_MINUTES_MILLIS = 10 * 60 * 1000L
    }
}
