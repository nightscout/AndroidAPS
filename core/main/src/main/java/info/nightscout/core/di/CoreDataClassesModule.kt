package info.nightscout.core.di

import app.aaps.interfaces.pump.PumpEnactResult
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.wizard.BolusWizard
import info.nightscout.core.wizard.QuickWizardEntry

@Module
@Suppress("unused")
abstract class CoreDataClassesModule {

    @ContributesAndroidInjector abstract fun pumpEnactResultInjector(): PumpEnactResult
    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}
