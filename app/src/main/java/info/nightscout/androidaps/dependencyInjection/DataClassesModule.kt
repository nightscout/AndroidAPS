package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.plugins.treatments.TreatmentService
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry

@Module
@Suppress("unused")
abstract class DataClassesModule {

    @ContributesAndroidInjector abstract fun glucoseStatusInjector(): GlucoseStatus

    @ContributesAndroidInjector abstract fun treatmentServiceInjector(): TreatmentService

    @ContributesAndroidInjector abstract fun bolusWizardInjector(): BolusWizard
    @ContributesAndroidInjector abstract fun quickWizardEntryInjector(): QuickWizardEntry
}