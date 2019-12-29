package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.receivers.KeepAliveReceiver

@Module
@Suppress("unused")
abstract class ReceiversModule {

    @ContributesAndroidInjector abstract fun contributesKeepAliveReceiver(): KeepAliveReceiver
}