package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkBroadcastReceiver
import info.nightscout.androidaps.plugins.aps.loop.CarbSuggestionReceiver
import info.nightscout.androidaps.receivers.*


@Module
@Suppress("unused")
abstract class ReceiversModule {

    @ContributesAndroidInjector abstract fun contributesAutoStartReceiver(): AutoStartReceiver
    @ContributesAndroidInjector abstract fun contributesBTReceiver(): BTReceiver
    @ContributesAndroidInjector abstract fun contributesChargingStateReceiver(): ChargingStateReceiver
    @ContributesAndroidInjector abstract fun contributesDataReceiver(): DataReceiver
    @ContributesAndroidInjector abstract fun contributesKeepAliveReceiver(): KeepAliveReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBluetoothStateReceiver(): RileyLinkBluetoothStateReceiver
    @ContributesAndroidInjector abstract fun contributesSmsReceiver(): SmsReceiver
    @ContributesAndroidInjector abstract fun contributesTimeDateOrTZChangeReceiver(): TimeDateOrTZChangeReceiver
    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBroadcastReceiver(): RileyLinkBroadcastReceiver
}
