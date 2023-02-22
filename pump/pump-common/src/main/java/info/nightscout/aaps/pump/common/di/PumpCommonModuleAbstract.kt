package info.nightscout.aaps.pump.common.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.aaps.pump.common.ble.BondStateReceiver
import info.nightscout.aaps.pump.common.ui.PumpBLEConfigActivity
import info.nightscout.aaps.pump.common.ui.PumpHistoryActivity

@Module
@Suppress("unused")
abstract class PumpCommonModuleAbstract {

    @ContributesAndroidInjector abstract fun contributesBondStateReceiver(): BondStateReceiver
    @ContributesAndroidInjector abstract fun contributesPumpBLEConfigActivity(): PumpBLEConfigActivity
    @ContributesAndroidInjector abstract fun contributesPumpHistoryActivity(): PumpHistoryActivity

}
