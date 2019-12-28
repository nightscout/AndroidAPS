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

@Module
abstract class FragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSAMAFragment(): OpenAPSAMAFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSMAFragment(): OpenAPSMAFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSSMBFragment(): OpenAPSSMBFragment
    @ContributesAndroidInjector abstract fun contributesNSProfileFragment(): NSProfileFragment
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
    @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog

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