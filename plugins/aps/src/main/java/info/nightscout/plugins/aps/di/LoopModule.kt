package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.aps.loop.CarbSuggestionReceiver
import info.nightscout.plugins.aps.loop.LoopFragment

@Module
@Suppress("unused")
abstract class LoopModule {
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): LoopFragment
    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver
}