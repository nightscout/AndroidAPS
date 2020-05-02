package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.*
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.queue.commands.*

@Module
@Suppress("unused")
abstract class MedtronicModule {
    @ContributesAndroidInjector abstract fun rileyLinkCommunicationManagerProvider(): RileyLinkCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicCommunicationManagerProvider(): MedtronicCommunicationManager
    @ContributesAndroidInjector abstract fun medtronicUITaskProvider(): MedtronicUITask
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