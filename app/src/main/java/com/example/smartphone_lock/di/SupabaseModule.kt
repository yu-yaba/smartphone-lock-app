package com.example.smartphone_lock.di

import com.example.smartphone_lock.config.SupabaseConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        configRepository: SupabaseConfigRepository
    ): SupabaseClient {
        val config = configRepository.fetch()
        val url = requireNotNull(config.url) {
            "Supabase URL is missing. Define SUPABASE_URL in local.properties."
        }
        val anonKey = requireNotNull(config.anonKey) {
            "Supabase anon key is missing. Define SUPABASE_ANON_KEY in local.properties."
        }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        )
    }
}
