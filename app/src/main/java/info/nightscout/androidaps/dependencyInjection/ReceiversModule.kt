package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import info.nightscout.androidaps.receivers.KeepAliveReceiver
import info.nightscout.androidaps.receivers.NetworkChangeReceiver
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver

@Module
@Suppress("unused")
abstract class ReceiversModule {

    @ContributesAndroidInjector abstract fun contributesKeepAliveReceiver(): KeepAliveReceiver
    @ContributesAndroidInjector abstract fun contributesNetworkChangeReceiver(): NetworkChangeReceiver
    @ContributesAndroidInjector abstract fun contributesTimeDateOrTZChangeReceiver(): TimeDateOrTZChangeReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBluetoothStateReceiver(): RileyLinkBluetoothStateReceiver
}