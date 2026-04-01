package app.aaps.pump.insight.di

import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.connection_service.InsightConnectionService
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class InsightServicesModule {

    @ContributesAndroidInjector abstract fun contributesInsightAlertService(): InsightAlertService
    @ContributesAndroidInjector abstract fun contributesInsightConnectionService(): InsightConnectionService
}