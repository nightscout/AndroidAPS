package info.nightscout.ui.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.ui.activities.BolusProgressHelperActivity
import info.nightscout.ui.activities.ErrorHelperActivity
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
import info.nightscout.ui.dialogs.CalibrationDialog
import info.nightscout.ui.dialogs.CarbsDialog
import info.nightscout.ui.dialogs.CareDialog
import info.nightscout.ui.dialogs.WizardInfoDialog

@Module
@Suppress("unused")
abstract class UiModule {

    @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @ContributesAndroidInjector abstract fun contributesWizardInfoDialog(): WizardInfoDialog

    @ContributesAndroidInjector abstract fun contributesTDDStatsActivity(): TDDStatsActivity
    @ContributesAndroidInjector abstract fun contributeBolusProgressHelperActivity(): BolusProgressHelperActivity
    @ContributesAndroidInjector abstract fun contributeErrorHelperActivity(): ErrorHelperActivity
    @ContributesAndroidInjector abstract fun contributesStatsActivity(): StatsActivity
    @ContributesAndroidInjector abstract fun contributesSurveyActivity(): SurveyActivity
    @ContributesAndroidInjector abstract fun contributesTreatmentsActivity(): TreatmentsActivity

    @ContributesAndroidInjector abstract fun contributesTreatmentsBolusFragment(): TreatmentsBolusCarbsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTemporaryBasalsFragment(): TreatmentsTemporaryBasalsFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsTempTargetFragment(): TreatmentsTempTargetFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsExtendedBolusesFragment(): TreatmentsExtendedBolusesFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsCareportalFragment(): TreatmentsCareportalFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsProfileSwitchFragment(): TreatmentsProfileSwitchFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsUserEntryFragment(): TreatmentsUserEntryFragment

}