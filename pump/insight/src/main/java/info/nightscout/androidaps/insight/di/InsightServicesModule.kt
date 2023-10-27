package info.nightscout.androidaps.insight.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.insight.InsightAlertService
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService

@Module
@Suppress("unused")
abstract class InsightServicesModule {

    @ContributesAndroidInjector abstract fun contributesInsightAlertService(): InsightAlertService
    @ContributesAndroidInjector abstract fun contributesInsightConnectionService(): InsightConnectionService
}