package info.nightscout.ui.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.ui.activities.BolusProgressHelperActivity
import info.nightscout.ui.activities.ErrorHelperActivity
import info.nightscout.ui.activities.ProfileHelperActivity
import info.nightscout.ui.activities.QuickWizardListActivity
import info.nightscout.ui.activities.StatsActivity
import info.nightscout.ui.activities.SurveyActivity
import info.nightscout.ui.activities.TDDStatsActivity
import info.nightscout.ui.activities.TreatmentsActivity
import info.nightscout.ui.activities.fragments.TreatmentsBolusCarbsFragment
import info.nightscout.ui.activities.fragments.TreatmentsCareportalFragment
import info.nightscout.ui.activities.fragments.TreatmentsExtendedBolusesFragment
import info.nightscout.ui.activities.fragments.TreatmentsProfileSwitchFragment
import info.nightscout.ui.activities.fragments.TreatmentsTempTargetFragment
import info.nightscout.ui.activities.fragments.TreatmentsTemporaryBasalsFragment
import info.nightscout.ui.activities.fragments.TreatmentsUserEntryFragment
import info.nightscout.ui.alertDialogs.ErrorDialog
import info.nightscout.ui.dialogs.BolusProgressDialog
import info.nightscout.ui.dialogs.CalibrationDialog
import info.nightscout.ui.dialogs.CarbsDialog
import info.nightscout.ui.dialogs.CareDialog
import info.nightscout.ui.dialogs.EditQuickWizardDialog
import info.nightscout.ui.dialogs.ExtendedBolusDialog
import info.nightscout.ui.dialogs.FillDialog
import info.nightscout.ui.dialogs.InsulinDialog
import info.nightscout.ui.dialogs.LoopDialog
import info.nightscout.ui.dialogs.ProfileSwitchDialog
import info.nightscout.ui.dialogs.ProfileViewerDialog
import info.nightscout.ui.dialogs.TempBasalDialog
import info.nightscout.ui.dialogs.TempTargetDialog
import info.nightscout.ui.dialogs.TreatmentDialog
import info.nightscout.ui.dialogs.WizardDialog
import info.nightscout.ui.dialogs.WizardInfoDialog
import info.nightscout.ui.services.AlarmSoundService
import info.nightscout.ui.widget.Widget
import info.nightscout.ui.widget.WidgetConfigureActivity

@Module
@Suppress("unused")
abstract class UiModule {

    @ContributesAndroidInjector abstract fun contributesAlarmSoundService(): AlarmSoundService

    @ContributesAndroidInjector abstract fun contributesWidget(): Widget
    @ContributesAndroidInjector abstract fun contributesWidgetConfigureActivity(): WidgetConfigureActivity

    @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
    @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @ContributesAndroidInjector abstract fun contributesWizardInfoDialog(): WizardInfoDialog
    @ContributesAndroidInjector abstract fun contributesProfileViewerDialog(): ProfileViewerDialog
    @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog
    @ContributesAndroidInjector abstract fun contributesInsulinDialog(): InsulinDialog
    @ContributesAndroidInjector abstract fun contributesTreatmentDialog(): TreatmentDialog
    @ContributesAndroidInjector abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog
    @ContributesAndroidInjector abstract fun contributesTempBasalDialog(): TempBasalDialog
    @ContributesAndroidInjector abstract fun contributesTempTargetDialog(): TempTargetDialog
    @ContributesAndroidInjector abstract fun contributesLoopDialog(): LoopDialog
    @ContributesAndroidInjector abstract fun contributesBolusProgressDialog(): BolusProgressDialog
    @ContributesAndroidInjector abstract fun contributesErrorDialog(): ErrorDialog
    @ContributesAndroidInjector abstract fun contributesQuickWizardListActivity(): QuickWizardListActivity
    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog

    @ContributesAndroidInjector abstract fun contributesTDDStatsActivity(): TDDStatsActivity
    @ContributesAndroidInjector abstract fun contributeBolusProgressHelperActivity(): BolusProgressHelperActivity
    @ContributesAndroidInjector abstract fun contributeErrorHelperActivity(): ErrorHelperActivity
    @ContributesAndroidInjector abstract fun contributesStatsActivity(): StatsActivity
    @ContributesAndroidInjector abstract fun contributesSurveyActivity(): SurveyActivity
    @ContributesAndroidInjector abstract fun contributesTreatmentsActivity(): TreatmentsActivity
    @ContributesAndroidInjector abstract fun contributesProfileHelperActivityActivity(): ProfileHelperActivity

    @ContributesAndroidInjector abstract fun contributesTreatmentsBolusFragment(): TreatmentsBolusCarbsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTemporaryBasalsFragment(): TreatmentsTemporaryBasalsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTempTargetFragment(): TreatmentsTempTargetFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsExtendedBolusesFragment(): TreatmentsExtendedBolusesFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsCareportalFragment(): TreatmentsCareportalFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsProfileSwitchFragment(): TreatmentsProfileSwitchFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsUserEntryFragment(): TreatmentsUserEntryFragment

}