package jp.kawai.ultrafocus.di

import jp.kawai.ultrafocus.service.ForegroundAppEventSource
import jp.kawai.ultrafocus.service.ForegroundAppMonitor
import jp.kawai.ultrafocus.service.UsageStatsForegroundAppEventSource
import jp.kawai.ultrafocus.service.UsageWatcher
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
