package info.nightscout.androidaps.plugins.pump.common.dagger

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.*

@Module
@Suppress("unused")
abstract class RileyLinkModule {
    @ContributesAndroidInjector abstract fun serviceTaskProvider(): ServiceTask
    @ContributesAndroidInjector abstract fun pumpTaskProvider(): PumpTask
    @ContributesAndroidInjector abstract fun discoverGattServicesTaskProvider(): DiscoverGattServicesTask
    @ContributesAndroidInjector abstract fun initializePumpManagerTaskProvider(): InitializePumpManagerTask
    @ContributesAndroidInjector abstract fun resetRileyLinkConfigurationTaskProvider(): ResetRileyLinkConfigurationTask
    @ContributesAndroidInjector abstract fun wakeAndTuneTaskProvider(): WakeAndTuneTask
    @ContributesAndroidInjector abstract fun radioResponseProvider(): RadioResponse
    @ContributesAndroidInjector abstract fun rileyLinkBLEProvider(): RileyLinkBLE
    @ContributesAndroidInjector abstract fun rfSpyProvider(): RFSpy
    @ContributesAndroidInjector abstract fun sendAndListenProvider(): SendAndListen
    @ContributesAndroidInjector abstract fun setPreambleProvider(): SetPreamble
    @ContributesAndroidInjector abstract fun radioPacketProvider(): RadioPacket
}