package com.example.smartphone_lock.di

import android.util.Log
import com.example.smartphone_lock.config.SupabaseConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.android.Android
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        configRepository: SupabaseConfigRepository
    ): SupabaseClient? {
        val config = configRepository.fetch()
        val url = config.url?.takeIf { it.isNotBlank() }
        val anonKey = config.anonKey?.takeIf { it.isNotBlank() }

        if (url == null || anonKey == null) {
            Log.i(TAG, "Supabase disabled: missing URL or anon key; skipping client init")
            return null
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            httpEngine = Android.create()
        }
    }

    private const val TAG = "SupabaseModule"
}
