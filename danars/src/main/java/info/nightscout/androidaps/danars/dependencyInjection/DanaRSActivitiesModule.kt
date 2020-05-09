package info.nightscout.androidaps.danars.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danars.activities.BLEScanActivity
import info.nightscout.androidaps.danars.activities.PairingHelperActivity

@Module
@Suppress("unused")
abstract class DanaRSActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBLEScanActivity(): BLEScanActivity
    @ContributesAndroidInjector abstract fun contributesPairingHelperActivity(): PairingHelperActivity
}