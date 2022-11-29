package info.nightscout.pump.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.danars.activities.BLEScanActivity
import info.nightscout.pump.danars.activities.EnterPinActivity
import info.nightscout.pump.danars.activities.PairingHelperActivity
import info.nightscout.pump.danars.dialogs.PairingProgressDialog

@Module
@Suppress("unused")
abstract class DanaRSActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBLEScanActivity(): BLEScanActivity
    @ContributesAndroidInjector abstract fun contributesPairingHelperActivity(): PairingHelperActivity
    @ContributesAndroidInjector abstract fun contributeEnterPinActivity(): EnterPinActivity

    @ContributesAndroidInjector abstract fun contributesPairingProgressDialog(): PairingProgressDialog
}