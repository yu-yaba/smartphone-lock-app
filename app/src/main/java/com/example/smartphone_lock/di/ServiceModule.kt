package com.example.smartphone_lock.di

import com.example.smartphone_lock.service.ForegroundAppEventSource
import com.example.smartphone_lock.service.ForegroundAppMonitor
import com.example.smartphone_lock.service.UsageStatsForegroundAppEventSource
import com.example.smartphone_lock.service.UsageWatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    abstract fun bindForegroundAppMonitor(usageWatcher: UsageWatcher): ForegroundAppMonitor

    @Binds
    abstract fun bindForegroundAppEventSource(
        usageStatsForegroundAppEventSource: UsageStatsForegroundAppEventSource,
    ): ForegroundAppEventSource
}
