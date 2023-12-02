package app.aaps.core.main.di

import app.aaps.core.main.wizard.BolusWizard
import app.aaps.core.main.wizard.QuickWizardEntry
import app.aaps.core.interfaces.pump.PumpEnactResult
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class CoreDataClassesModule {

    @ContributesAndroidInjector abstract fun pumpEnactResultInjector(): PumpEnactResult
    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}
