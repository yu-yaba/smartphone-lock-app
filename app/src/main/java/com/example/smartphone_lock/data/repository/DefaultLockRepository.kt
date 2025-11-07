package com.example.smartphone_lock.data.repository

import android.content.Context
import android.util.Log
import com.example.smartphone_lock.data.datastore.DataStoreManager
import com.example.smartphone_lock.data.datastore.LockStatePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ロック状態と封鎖対象アプリの管理を行うデフォルト実装。
 */
@Singleton
class DefaultLockRepository @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext context: Context,
) : LockRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val customBlacklist = MutableStateFlow(emptySet<String>())
    private val customWhitelist = MutableStateFlow(emptySet<String>())
    private val blockUnknownPackages = MutableStateFlow(true)

    private val baseWhitelist = DEFAULT_WHITELIST + context.packageName

    private val blacklistState: StateFlow<Set<String>> = customBlacklist
        .map { custom -> DEFAULT_BLACKLIST + custom }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_BLACKLIST)

    private val whitelistState: StateFlow<Set<String>> = customWhitelist
        .map { custom -> baseWhitelist + custom }
        .stateIn(scope, SharingStarted.Eagerly, baseWhitelist)

    override val lockState: Flow<LockStatePreferences> = dataStoreManager.lockState

    override fun shouldBlockPackage(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return false
        if (isWhitelisted(normalized)) return false
        val isBlacklisted = blacklistState.value.contains(normalized)
        val shouldBlock = isBlacklisted || blockUnknownPackages.value
        if (shouldBlock) {
            val reason = if (isBlacklisted) "blacklist" else "not_whitelisted"
            Log.d(TAG, "Blocking package detected ($reason): $normalized")
        }
        return shouldBlock
    }

    override fun isWhitelisted(packageName: String): Boolean {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return false
        val isAllowed = whitelistState.value.contains(normalized)
        if (isAllowed) {
            Log.v(TAG, "Whitelisted package detected: $normalized")
        }
        return isAllowed
    }

    /**
     * ブラックリストの上書きを行う。現状はインメモリ保持だが、
     * 将来的に DataStore 保存に差し替え可能な形で公開しておく。
     */
    fun updateCustomBlacklist(packages: Set<String>) {
        scope.launch { customBlacklist.emit(packages) }
    }

    /**
     * ホワイトリストの上書きを行う。現状はインメモリ保持。
     */
    fun updateCustomWhitelist(packages: Set<String>) {
        scope.launch { customWhitelist.emit(packages) }
    }

    /**
     * ホワイトリスト以外のパッケージを一律で封鎖するかを設定する。
     */
    fun setBlockUnknownPackages(enabled: Boolean) {
        scope.launch { blockUnknownPackages.emit(enabled) }
    }

    companion object {
        private const val TAG = "DefaultLockRepo"

        private val DEFAULT_BLACKLIST = setOf(
            "com.android.settings",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        private val DEFAULT_WHITELIST = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.android.phone",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
        )
    }
}
