package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAFragment
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAFragment
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBFragment
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorFragment
import info.nightscout.androidaps.plugins.profile.ns.NSProfileFragment
import info.nightscout.androidaps.plugins.treatments.TreatmentsFragment

@Module
abstract class FragmentsModule {

    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesOpenAPSAMAFragment(): OpenAPSAMAFragment
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesOpenAPSMAFragment(): OpenAPSMAFragment
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesOpenAPSSMBFragment(): OpenAPSSMBFragment
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesNSProfileFragment(): NSProfileFragment
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesTreatmentsFragment(): TreatmentsFragment

    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesInsulinDialog(): InsulinDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesProfileViewerDialog(): ProfileViewerDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesTempBasalDialog(): TempBasalDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesTempTargetDialog(): TempTargetDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesTreatmentDialog(): TreatmentDialog
    @Suppress("unused") @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
}