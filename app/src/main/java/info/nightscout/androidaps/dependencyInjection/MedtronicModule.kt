package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask

@Module
@Suppress("unused")
abstract class MedtronicModule {
    @ContributesAndroidInjector abstract fun rileyLinkCommunicationManagerProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicCommunicationManagerProvider(): info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProvider(): info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask
    @ContributesAndroidInjector abstract fun serviceTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask
    @ContributesAndroidInjector abstract fun pumpTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.PumpTask
    @ContributesAndroidInjector abstract fun discoverGattServicesTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask
    @ContributesAndroidInjector abstract fun initializePumpManagerTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask
    @ContributesAndroidInjector abstract fun resetRileyLinkConfigurationTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
    @ContributesAndroidInjector abstract fun wakeAndTuneTaskProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
    @ContributesAndroidInjector abstract fun radioResponseProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
    @ContributesAndroidInjector abstract fun rileyLinkBLEProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
    @ContributesAndroidInjector abstract fun rfSpyProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
    @ContributesAndroidInjector abstract fun sendAndListenProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen
    @ContributesAndroidInjector abstract fun setPreambleProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble
    @ContributesAndroidInjector abstract fun radioPacketProvider(): info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
}