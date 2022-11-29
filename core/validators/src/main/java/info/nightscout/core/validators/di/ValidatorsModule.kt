package info.nightscout.core.validators.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.validators.DefaultEditTextValidator
import info.nightscout.core.validators.EditTextValidator
import info.nightscout.core.validators.ValidatingEditTextPreference

@Module
@Suppress("unused")
abstract class ValidatorsModule {

    @ContributesAndroidInjector abstract fun defaultEditTextValidatorInjector(): DefaultEditTextValidator
    @ContributesAndroidInjector abstract fun editTextValidatorInjector(): EditTextValidator
    @ContributesAndroidInjector abstract fun validatingEditTextPreferenceInjector(): ValidatingEditTextPreference
}
