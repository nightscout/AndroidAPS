package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment
import info.nightscout.androidaps.plugins.general.maintenance.MaintenanceFragment
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.wear.WearFragment

@Module
@Suppress("unused")
abstract class FragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun contributesMaintenanceFragment(): MaintenanceFragment
    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment

    @ContributesAndroidInjector abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog
}