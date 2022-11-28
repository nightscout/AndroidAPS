package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.utils.receivers.NetworkChangeReceiver

@Module
abstract class CoreReceiversModule {

    @ContributesAndroidInjector abstract fun contributesNetworkChangeReceiver(): NetworkChangeReceiver
}