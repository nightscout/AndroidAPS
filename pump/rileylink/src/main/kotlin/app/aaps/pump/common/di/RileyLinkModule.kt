package app.aaps.pump.common.di

import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse
import app.aaps.pump.common.hw.rileylink.ble.device.OrangeLinkImpl
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBroadcastReceiver
import app.aaps.pump.common.hw.rileylink.service.RileyLinkService
import app.aaps.pump.common.hw.rileylink.service.tasks.DiscoverGattServicesTask
import app.aaps.pump.common.hw.rileylink.service.tasks.InitializePumpManagerTask
import app.aaps.pump.common.hw.rileylink.service.tasks.PumpTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTask
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
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
    @ContributesAndroidInjector abstract fun orangeLinkDeviceProvider(): OrangeLinkImpl

    @ContributesAndroidInjector abstract fun contributesRileyLinkService(): RileyLinkService
    @ContributesAndroidInjector abstract fun contributesRileyLinkBroadcastReceiver(): RileyLinkBroadcastReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBluetoothStateReceiver(): RileyLinkBluetoothStateReceiver
}