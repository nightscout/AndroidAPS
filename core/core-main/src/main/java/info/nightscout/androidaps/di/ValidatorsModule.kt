package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.utils.textValidator.DefaultEditTextValidator
import info.nightscout.androidaps.utils.textValidator.EditTextValidator
import info.nightscout.androidaps.utils.textValidator.ValidatingEditTextPreference

@Module
@Suppress("unused")
abstract class ValidatorsModule {

    @ContributesAndroidInjector abstract fun defaultEditTextValidatorInjector(): DefaultEditTextValidator
    @ContributesAndroidInjector abstract fun editTextValidatorInjector(): EditTextValidator
    @ContributesAndroidInjector abstract fun validatingEditTextPreferenceInjector(): ValidatingEditTextPreference
}
