package app.aaps.di

import app.aaps.receivers.AutoStartReceiver
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.DataReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.SmsReceiver
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import dagger.Module
import dagger.android.ContributesAndroidInjector

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
