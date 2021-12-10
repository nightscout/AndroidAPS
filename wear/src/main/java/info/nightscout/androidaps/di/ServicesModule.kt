package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.complications.BaseComplicationProviderService
import info.nightscout.androidaps.data.ListenerService

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesListenerService(): ListenerService
    @ContributesAndroidInjector abstract fun contributesBaseComplicationProviderService(): BaseComplicationProviderService
}