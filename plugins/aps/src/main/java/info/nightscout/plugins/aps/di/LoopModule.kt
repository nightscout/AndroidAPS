package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class LoopModule {
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): info.nightscout.plugins.aps.loop.LoopFragment
    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): info.nightscout.plugins.aps.loop.CarbSuggestionReceiver
}