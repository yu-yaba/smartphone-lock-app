package jp.kawai.ultrafocus.service

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 特定パッケージのイベントを一定間隔内で抑制するデバウンサー。
 */
class PackageEventThrottler(
    private val debounceMillis: Long,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {

    private val mutex = Mutex()
    private val lastTriggerTimestamp = mutableMapOf<String, Long>()

    suspend fun shouldTrigger(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return false
        val now = clock()
        return mutex.withLock {
            val last = lastTriggerTimestamp[normalized]
            if (last != null && now - last < debounceMillis) {
                false
            } else {
                lastTriggerTimestamp[normalized] = now
                true
            }
        }
    }
}
