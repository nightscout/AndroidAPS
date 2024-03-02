package app.aaps.core.validators.di

import app.aaps.core.validators.AdaptiveDoublePreference
import app.aaps.core.validators.AdaptiveIntPreference
import app.aaps.core.validators.AdaptiveStringPreference
import app.aaps.core.validators.AdaptiveSwitchPreference
import app.aaps.core.validators.AdaptiveUnitPreference
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.ValidatingEditTextPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ValidatorsModule {

    @ContributesAndroidInjector abstract fun defaultEditTextValidatorInjector(): DefaultEditTextValidator
    @ContributesAndroidInjector abstract fun editTextValidatorInjector(): EditTextValidator
    @ContributesAndroidInjector abstract fun validatingEditTextPreferenceInjector(): ValidatingEditTextPreference
    @ContributesAndroidInjector abstract fun adaptiveUnitPreferenceInjector(): AdaptiveUnitPreference
    @ContributesAndroidInjector abstract fun adaptiveIntPreferenceInjector(): AdaptiveIntPreference
    @ContributesAndroidInjector abstract fun adaptiveDoublePreferenceInjector(): AdaptiveDoublePreference
    @ContributesAndroidInjector abstract fun adaptiveStringPreferenceInjector(): AdaptiveStringPreference
    @ContributesAndroidInjector abstract fun adaptiveSwitchPreferenceInjector(): AdaptiveSwitchPreference

}
