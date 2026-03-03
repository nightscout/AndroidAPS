package app.aaps.pump.insight.di

import app.aaps.pump.insight.InsightFragment
import app.aaps.pump.insight.app_layer.activities.InsightAlertActivity
import app.aaps.pump.insight.app_layer.activities.InsightPairingActivity
import app.aaps.pump.insight.app_layer.activities.InsightPairingInformationActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class InsightActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesInsightAlertActivity(): InsightAlertActivity
    @ContributesAndroidInjector abstract fun contributesInsightPairingActivity(): InsightPairingActivity
    @ContributesAndroidInjector abstract fun contributesInsightPairingInformationActivity(): InsightPairingInformationActivity

    @ContributesAndroidInjector abstract fun contributesLocalInsightFragment(): InsightFragment
}