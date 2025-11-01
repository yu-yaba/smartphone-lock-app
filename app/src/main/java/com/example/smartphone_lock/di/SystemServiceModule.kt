package com.example.smartphone_lock.di

import android.app.admin.DevicePolicyManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {

    @Provides
    @Singleton
    fun provideDevicePolicyManager(
        @ApplicationContext context: Context
    ): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
}
