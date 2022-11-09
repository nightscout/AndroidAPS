package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.receivers.NetworkChangeReceiver

@Module
abstract class CoreReceiversModule {

    @ContributesAndroidInjector abstract fun contributesNetworkChangeReceiver(): NetworkChangeReceiver
}