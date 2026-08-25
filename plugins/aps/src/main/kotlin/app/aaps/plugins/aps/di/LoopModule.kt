package app.aaps.plugins.aps.di

import app.aaps.plugins.aps.loop.CarbSuggestionReceiver
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class LoopModule {
    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver
}