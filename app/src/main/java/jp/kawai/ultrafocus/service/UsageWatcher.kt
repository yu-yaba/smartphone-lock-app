package jp.kawai.ultrafocus.service

import android.util.Log
import jp.kawai.ultrafocus.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * UsageStats ベースでフォアグラウンドアプリを監視する実装。
 */
@Singleton
class UsageWatcher @Inject constructor(
    private val eventSource: ForegroundAppEventSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ForegroundAppMonitor {

    private val modeChangeSignals = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var currentPollingIntervalMillis: Long = POLL_INTERVAL_MILLIS

    override fun start(scope: CoroutineScope, onMoveToForeground: (String) -> Unit) {
        if (watchJobActive) {
            Log.d(TAG, "Usage watcher already running")
            return
        }
        watchJobActive = true
        val job = scope.launch(dispatcher) {
            try {
                pollUsageEvents(onMoveToForeground)
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
        job.invokeOnCompletion { watchJobActive = false }
    }

    override fun setHighFrequencyMode(enabled: Boolean) {
        val nextInterval = if (enabled) HIGH_FREQUENCY_POLL_INTERVAL_MILLIS else POLL_INTERVAL_MILLIS
        if (currentPollingIntervalMillis == nextInterval) return
        currentPollingIntervalMillis = nextInterval
        modeChangeSignals.trySend(Unit)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "home_overlay_monitor step=mode_changed highFrequency=$enabled " +
                    "pollIntervalMillis=$nextInterval"
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun pollUsageEvents(onMoveToForeground: (String) -> Unit) {
        var lastKnownForeground: String? = null
        while (coroutineContext.isActive) {
            var eventEmitted = false
            try {
                eventSource.collectRecentEvents(QUERY_WINDOW_MILLIS) { packageName ->
                    val normalized = packageName.trim()
                    if (normalized.isNotEmpty()) {
                        lastKnownForeground = normalized
                        eventEmitted = true
                        onMoveToForeground(normalized)
                    }
                }
                if (!eventEmitted) {
                    lastKnownForeground?.let { onMoveToForeground(it) }
                }
            } catch (security: SecurityException) {
                Log.w(TAG, "Usage access permission missing", security)
                delay(PERMISSION_RETRY_DELAY_MILLIS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Unexpected error while polling usage events", throwable)
            }
            select<Unit> {
                modeChangeSignals.onReceive { }
                onTimeout(currentPollingIntervalMillis) { }
            }
        }
    }

    companion object {
        private const val TAG = "UsageWatcher"
        internal const val POLL_INTERVAL_MILLIS = 750L
        internal const val HIGH_FREQUENCY_POLL_INTERVAL_MILLIS = 250L
        internal const val QUERY_WINDOW_MILLIS = 2_000L
        internal const val PERMISSION_RETRY_DELAY_MILLIS = 5_000L

        @Volatile
        private var watchJobActive: Boolean = false
    }
}
