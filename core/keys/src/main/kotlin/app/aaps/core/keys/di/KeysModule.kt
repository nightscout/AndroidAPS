package app.aaps.core.keys.di

import app.aaps.core.keys.AdaptiveIntentPreference
import app.aaps.core.keys.AdaptiveListPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class KeysModule {

    @ContributesAndroidInjector abstract fun adaptiveIntentPreferenceInjector(): AdaptiveIntentPreference
    @ContributesAndroidInjector abstract fun adaptiveListPreferenceInjector(): AdaptiveListPreference
}
