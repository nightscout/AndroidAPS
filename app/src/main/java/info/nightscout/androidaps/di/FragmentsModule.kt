package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.plugins.constraints.objectives.dialogs.NtpProgressDialog
import info.nightscout.androidaps.plugins.aps.OpenAPSFragment
import info.nightscout.androidaps.plugins.aps.loop.LoopFragment
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment
import info.nightscout.plugins.constraints.objectives.ObjectivesFragment
import info.nightscout.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.plugins.general.actions.ActionsFragment
import info.nightscout.androidaps.plugins.general.maintenance.MaintenanceFragment
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.wear.WearFragment
import info.nightscout.androidaps.utils.protection.PasswordCheck
import info.nightscout.ui.dialogs.WizardDialog

@Module
@Suppress("unused")
abstract class FragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector abstract fun contributesActionsFragment(): ActionsFragment
    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): LoopFragment
    @ContributesAndroidInjector abstract fun contributesMaintenanceFragment(): MaintenanceFragment
    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment

    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog

    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
    @ContributesAndroidInjector abstract fun contributesNtpProgressDialog(): NtpProgressDialog
    @ContributesAndroidInjector abstract fun contributesPasswordCheck(): PasswordCheck
}