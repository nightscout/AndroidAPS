package info.nightscout.androidaps.insight.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightFragment
import info.nightscout.androidaps.plugins.pump.insight.activities.InsightAlertActivity
import info.nightscout.androidaps.plugins.pump.insight.activities.InsightPairingActivity
import info.nightscout.androidaps.plugins.pump.insight.activities.InsightPairingInformationActivity

@Module
@Suppress("unused")
abstract class InsightActivitiesModule {
    @ContributesAndroidInjector abstract fun contributesInsightAlertActivity(): InsightAlertActivity
    @ContributesAndroidInjector abstract fun contributesInsightPairingActivity(): InsightPairingActivity
    @ContributesAndroidInjector abstract fun contributesInsightPairingInformationActivity(): InsightPairingInformationActivity

    @ContributesAndroidInjector abstract fun contributesLocalInsightFragment(): LocalInsightFragment
}