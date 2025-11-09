package com.example.smartphone_lock

import android.app.Application
import android.util.Log
import com.example.smartphone_lock.data.repository.LockRepository
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@HiltAndroidApp
class SmartphoneLockApplication : Application() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var lockRepository: LockRepository

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Supabase initialized: success")
        lockRepository.refreshDynamicLists()
    }

    private companion object {
        const val TAG = "SmartphoneLockApp"
    }
}
