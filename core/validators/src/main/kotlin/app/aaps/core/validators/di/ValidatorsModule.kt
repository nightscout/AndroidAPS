package app.aaps.core.validators.di

import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ValidatorsModule {

    @ContributesAndroidInjector abstract fun defaultEditTextValidatorInjector(): DefaultEditTextValidator
    @ContributesAndroidInjector abstract fun adaptiveUnitPreferenceInjector(): AdaptiveUnitPreference
    @ContributesAndroidInjector abstract fun adaptiveIntPreferenceInjector(): AdaptiveIntPreference
    @ContributesAndroidInjector abstract fun adaptiveDoublePreferenceInjector(): AdaptiveDoublePreference
    @ContributesAndroidInjector abstract fun adaptiveStringPreferenceInjector(): AdaptiveStringPreference
    @ContributesAndroidInjector abstract fun adaptiveSwitchPreferenceInjector(): AdaptiveSwitchPreference
    @ContributesAndroidInjector abstract fun adaptiveClickPreferenceInjector(): AdaptiveClickPreference
    @ContributesAndroidInjector abstract fun adaptiveIntentPreferenceInjector(): AdaptiveIntentPreference
    @ContributesAndroidInjector abstract fun adaptiveListPreferenceInjector(): AdaptiveListPreference
    @ContributesAndroidInjector abstract fun adaptiveListIntPreferenceInjector(): AdaptiveListIntPreference

}
