package info.nightscout.androidaps.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danars.activities.BLEScanActivity
import info.nightscout.androidaps.danars.activities.EnterPinActivity
import info.nightscout.androidaps.danars.activities.PairingHelperActivity
import info.nightscout.androidaps.danars.dialogs.PairingProgressDialog

@Module
@Suppress("unused")
abstract class DanaRSActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBLEScanActivity(): BLEScanActivity
    @ContributesAndroidInjector abstract fun contributesPairingHelperActivity(): PairingHelperActivity
    @ContributesAndroidInjector abstract fun contributeEnterPinActivity(): EnterPinActivity

    @ContributesAndroidInjector abstract fun contributesPairingProgressDialog(): PairingProgressDialog
}