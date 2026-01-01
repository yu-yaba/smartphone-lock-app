package jp.kawai.ultrafocus

import android.app.Application
import android.util.Log
import jp.kawai.ultrafocus.data.repository.LockRepository
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@HiltAndroidApp
class UltraFocusApplication : Application() {

    @Inject
    @JvmField
    var supabaseClient: SupabaseClient? = null

    @Inject
    lateinit var lockRepository: LockRepository

    override fun onCreate() {
        super.onCreate()
        supabaseClient?.let {
            Log.i(TAG, "Supabase initialized: success")
        } ?: Log.i(TAG, "Supabase disabled: no client initialized")
        lockRepository.refreshDynamicLists()
    }

    private companion object {
        const val TAG = "UltraFocusApp"
    }
}
