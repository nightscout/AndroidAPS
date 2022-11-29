package info.nightscout.androidaps.plugins.pump.common.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.dialog.RileyLinkBLEConfigActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.device.OrangeLinkImpl
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusActivity
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusGeneralFragment
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusHistoryFragment
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBroadcastReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.PumpTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask

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
    @ContributesAndroidInjector abstract fun orangeLinkDeviceProvider(): OrangeLinkImpl

    @ContributesAndroidInjector abstract fun contributesRileyLinkStatusGeneral(): RileyLinkStatusGeneralFragment
    @ContributesAndroidInjector abstract fun contributesRileyLinkStatusHistoryFragment(): RileyLinkStatusHistoryFragment

    @ContributesAndroidInjector abstract fun contributesRileyLinkStatusActivity(): RileyLinkStatusActivity
    @ContributesAndroidInjector abstract fun contributesRileyLinkBLEConfigActivity(): RileyLinkBLEConfigActivity

    @ContributesAndroidInjector abstract fun contributesRileyLinkService(): RileyLinkService
    @ContributesAndroidInjector abstract fun contributesRileyLinkBroadcastReceiver(): RileyLinkBroadcastReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBluetoothStateReceiver(): RileyLinkBluetoothStateReceiver
}