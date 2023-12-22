package app.aaps.pump.danars.di

import app.aaps.pump.danars.activities.BLEScanActivity
import app.aaps.pump.danars.activities.EnterPinActivity
import app.aaps.pump.danars.activities.PairingHelperActivity
import app.aaps.pump.danars.dialogs.PairingProgressDialog
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class DanaRSActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesBLEScanActivity(): BLEScanActivity
    @ContributesAndroidInjector abstract fun contributesPairingHelperActivity(): PairingHelperActivity
    @ContributesAndroidInjector abstract fun contributeEnterPinActivity(): EnterPinActivity

    @ContributesAndroidInjector abstract fun contributesPairingProgressDialog(): PairingProgressDialog
}