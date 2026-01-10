package jp.kawai.ultrafocus.data.repository

/**
 * ロック中に許可する外部アプリ（電話/SMS）の既定パッケージ。
 */
data class AllowedAppTargets(
    val dialerPackage: String?,
    val smsPackage: String?,
) {
    fun isAllowed(packageName: String): Boolean {
        return packageName == dialerPackage || packageName == smsPackage
    }

    fun isEmpty(): Boolean = dialerPackage.isNullOrBlank() && smsPackage.isNullOrBlank()
}
