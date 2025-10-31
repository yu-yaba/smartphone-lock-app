package com.example.smartphone_lock

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject

@HiltAndroidApp
class SmartphoneLockApplication : Application() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Supabase initialized: success")
    }

    private companion object {
        const val TAG = "SmartphoneLockApp"
    }
}
