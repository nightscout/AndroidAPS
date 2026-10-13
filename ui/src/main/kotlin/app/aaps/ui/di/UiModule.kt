package app.aaps.ui.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.ui.activities.BolusProgressHelperActivity
import app.aaps.ui.activities.ErrorHelperActivity
import app.aaps.ui.activities.ProfileHelperActivity
import app.aaps.ui.activities.QuickWizardListActivity
import app.aaps.ui.activities.StatsActivity
import app.aaps.ui.activities.SurveyActivity
import app.aaps.ui.activities.TDDStatsActivity
import app.aaps.ui.activities.TreatmentsActivity
import app.aaps.ui.activities.fragments.TreatmentsBolusCarbsFragment
import app.aaps.ui.activities.fragments.TreatmentsCareportalFragment
import app.aaps.ui.activities.fragments.TreatmentsExtendedBolusesFragment
import app.aaps.ui.activities.fragments.TreatmentsProfileSwitchFragment
import app.aaps.ui.activities.fragments.TreatmentsRunningModeFragment
import app.aaps.ui.activities.fragments.TreatmentsTempTargetFragment
import app.aaps.ui.activities.fragments.TreatmentsTemporaryBasalsFragment
import app.aaps.ui.activities.fragments.TreatmentsUserEntryFragment
import app.aaps.ui.alertDialogs.ErrorDialog
import app.aaps.ui.dialogs.BolusProgressDialog
import app.aaps.ui.dialogs.CalibrationDialog
import app.aaps.ui.dialogs.CarbsDialog
import app.aaps.ui.dialogs.CareDialog
import app.aaps.ui.dialogs.EditQuickWizardDialog
import app.aaps.ui.dialogs.ExtendedBolusDialog
import app.aaps.ui.dialogs.FillDialog
import app.aaps.ui.dialogs.InsulinDialog
import app.aaps.ui.dialogs.LoopDialog
import app.aaps.ui.dialogs.ProfileSwitchDialog
import app.aaps.ui.dialogs.ProfileViewerDialog
import app.aaps.ui.dialogs.SiteRotationDialog
import app.aaps.ui.dialogs.TempBasalDialog
import app.aaps.ui.dialogs.TempTargetDialog
import app.aaps.ui.dialogs.TreatmentDialog
import app.aaps.ui.dialogs.WizardDialog
import app.aaps.ui.dialogs.WizardInfoDialog
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.widget.Widget
import app.aaps.ui.widget.WidgetConfigureActivity

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
    @ContributesAndroidInjector abstract fun contributesSiteRotationDialog(): SiteRotationDialog
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
    @ContributesAndroidInjector abstract fun contributesTreatmentsRunningModeFragment(): TreatmentsRunningModeFragment

}