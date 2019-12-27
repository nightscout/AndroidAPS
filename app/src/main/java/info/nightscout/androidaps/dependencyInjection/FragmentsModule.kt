package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorFragment

@Module
abstract class FragmentsModule {

    @ContributesAndroidInjector
    abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector
    abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment

    @ContributesAndroidInjector
    abstract fun contributesCalibrationDialog(): CalibrationDialog

    @ContributesAndroidInjector
    abstract fun contributesCarbsDialog(): CarbsDialog

    @ContributesAndroidInjector
    abstract fun contributesCareDialog(): CareDialog

    @ContributesAndroidInjector
    abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog

    @ContributesAndroidInjector
    abstract fun contributesFillDialog(): FillDialog

    @ContributesAndroidInjector
    abstract fun contributesInsulinDialog(): InsulinDialog

    @ContributesAndroidInjector
    abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog

    @ContributesAndroidInjector
    abstract fun contributesProfileViewerDialog(): ProfileViewerDialog

    @ContributesAndroidInjector
    abstract fun contributesTempBasalDialog(): TempBasalDialog

    @ContributesAndroidInjector
    abstract fun contributesTempTargetDialog(): TempTargetDialog

    @ContributesAndroidInjector
    abstract fun contributesTreatmentDialog(): TreatmentDialog

    @ContributesAndroidInjector
    abstract fun contributesWizardDialog(): WizardDialog
}