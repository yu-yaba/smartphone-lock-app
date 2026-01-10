package jp.kawai.ultrafocus.util

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

object AllowedAppResolver {
    fun resolveDialerPackage(context: Context, explicit: String? = null): String? {
        explicit?.takeIf { it.isNotBlank() }?.let { return it }
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecomManager?.defaultDialerPackage?.let { return it }
        return null
    }

    fun resolveSmsPackage(context: Context, explicit: String? = null): String? {
        explicit?.takeIf { it.isNotBlank() }?.let { return it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }
}
