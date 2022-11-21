package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.aps.loop.CarbSuggestionReceiver

@Module
@Suppress("unused")
abstract class LoopModule {

    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver
}