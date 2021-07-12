package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry

@Module
@Suppress("unused")
abstract class DataClassesModule {

    @ContributesAndroidInjector abstract fun glucoseStatusInjector(): GlucoseStatus

    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}