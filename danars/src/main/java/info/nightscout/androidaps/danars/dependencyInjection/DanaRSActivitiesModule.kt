package info.nightscout.androidaps.danars.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.danaRS.activities.BLEScanActivity
import info.nightscout.androidaps.plugins.pump.danaRS.activities.PairingHelperActivity

@Module
@Suppress("unused")
abstract class DanaRSActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBLEScanActivity(): BLEScanActivity
    @ContributesAndroidInjector abstract fun contributesPairingHelperActivity(): PairingHelperActivity
}