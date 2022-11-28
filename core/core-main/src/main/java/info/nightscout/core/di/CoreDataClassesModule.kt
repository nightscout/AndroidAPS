package info.nightscout.core.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.core.iob.iobCobCalculator.data.AutosensDataObject
import info.nightscout.core.aps.APSResultObject
import info.nightscout.core.wizard.BolusWizard
import info.nightscout.core.wizard.QuickWizardEntry
import info.nightscout.interfaces.pump.PumpEnactResult

@Module
@Suppress("unused")
abstract class CoreDataClassesModule {

    @ContributesAndroidInjector abstract fun pumpEnactResultInjector(): PumpEnactResult
    @ContributesAndroidInjector abstract fun apsResultInjector(): APSResultObject
    @ContributesAndroidInjector abstract fun autosensDataInjector(): AutosensDataObject
    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}
