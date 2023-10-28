package app.aaps.pump.insight.di

import app.aaps.pump.insight.InsightAlertService
import app.aaps.pump.insight.connection_service.InsightConnectionService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class InsightServicesModule {

    @ContributesAndroidInjector abstract fun contributesInsightAlertService(): InsightAlertService
    @ContributesAndroidInjector abstract fun contributesInsightConnectionService(): InsightConnectionService
}