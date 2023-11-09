package app.aaps.core.validators.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.ValidatingEditTextPreference

@Module
@Suppress("unused")
abstract class ValidatorsModule {

    @ContributesAndroidInjector abstract fun defaultEditTextValidatorInjector(): DefaultEditTextValidator
    @ContributesAndroidInjector abstract fun editTextValidatorInjector(): EditTextValidator
    @ContributesAndroidInjector abstract fun validatingEditTextPreferenceInjector(): ValidatingEditTextPreference
}
