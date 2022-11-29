package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.receivers.AutoStartReceiver
import info.nightscout.androidaps.receivers.BTReceiver
import info.nightscout.androidaps.receivers.ChargingStateReceiver
import info.nightscout.androidaps.receivers.DataReceiver
import info.nightscout.androidaps.receivers.KeepAliveWorker
import info.nightscout.androidaps.receivers.SmsReceiver
import info.nightscout.androidaps.receivers.TimeDateOrTZChangeReceiver

@Module
@Suppress("unused")
abstract class ReceiversModule {

    @ContributesAndroidInjector abstract fun contributesAutoStartReceiver(): AutoStartReceiver
    @ContributesAndroidInjector abstract fun contributesBTReceiver(): BTReceiver
    @ContributesAndroidInjector abstract fun contributesChargingStateReceiver(): ChargingStateReceiver
    @ContributesAndroidInjector abstract fun contributesDataReceiver(): DataReceiver
    @ContributesAndroidInjector abstract fun contributesKeepAliveWorker(): KeepAliveWorker
    @ContributesAndroidInjector abstract fun contributesSmsReceiver(): SmsReceiver
    @ContributesAndroidInjector abstract fun contributesTimeDateOrTZChangeReceiver(): TimeDateOrTZChangeReceiver
}
