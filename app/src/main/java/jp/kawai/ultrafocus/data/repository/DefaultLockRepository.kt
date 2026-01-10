package jp.kawai.ultrafocus.data.repository

import android.content.Context
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.kawai.ultrafocus.data.datastore.DataStoreManager
import jp.kawai.ultrafocus.data.datastore.DeviceProtectedLockStatePreferences
import jp.kawai.ultrafocus.data.datastore.LockStatePreferences
import jp.kawai.ultrafocus.util.AllowedAppResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * ロック状態と設定アプリ検知のみを扱うシンプルな実装。
 */
@Singleton
class DefaultLockRepository @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val context: Context,
) : LockRepository {

    override val lockState: Flow<LockStatePreferences> = dataStoreManager.lockState

    override val deviceProtectedLockState: Flow<DeviceProtectedLockStatePreferences> =
        dataStoreManager.deviceProtectedLockState

    @Volatile
    private var allowedTargets: AllowedAppTargets = AllowedAppTargets(null, null)

    override fun shouldForceLockUi(packageName: String): Boolean {
        val normalized = packageName.trim()
        return normalized.isNotEmpty() && SettingsPackages.TARGETS.contains(normalized)
    }

    override fun allowedAppTargets(): AllowedAppTargets {
        val current = allowedTargets
        if (!current.isEmpty()) {
            return current
        }
        return resolveAllowedTargets().also { allowedTargets = it }
    }

    override fun refreshDynamicLists() {
        allowedTargets = resolveAllowedTargets()
    }

    private fun resolveAllowedTargets(): AllowedAppTargets {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val dialerPackage = telecomManager?.defaultDialerPackage
            ?: AllowedAppResolver.resolveDialerPackage(context)
        val smsPackage = AllowedAppResolver.resolveSmsPackage(context)
        return AllowedAppTargets(dialerPackage, smsPackage)
    }
}
