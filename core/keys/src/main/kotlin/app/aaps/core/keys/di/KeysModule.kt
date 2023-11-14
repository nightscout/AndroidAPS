package app.aaps.core.keys.di

import app.aaps.core.keys.AdaptiveListPreference
import app.aaps.core.keys.AdaptiveSwitchPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class KeysModule {

    @ContributesAndroidInjector abstract fun adaptiveSwitchPreferenceInjector(): AdaptiveSwitchPreference
    @ContributesAndroidInjector abstract fun adaptiveListPreferenceInjector(): AdaptiveListPreference
}
