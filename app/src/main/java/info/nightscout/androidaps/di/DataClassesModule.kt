package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.wizard.BolusWizard
import info.nightscout.core.wizard.QuickWizardEntry

@Module
@Suppress("unused")
abstract class DataClassesModule {

    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}