package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.activities.ProfileHelperActivity
import info.nightscout.androidaps.activities.SingleFragmentActivity
import info.nightscout.androidaps.activities.TreatmentsActivity
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.source.activities.RequestDexcomPermissionActivity
import info.nightscout.androidaps.setupwizard.SetupWizardActivity

@Module
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesTreatmentsActivity(): TreatmentsActivity
    @ContributesAndroidInjector abstract fun contributesHistoryBrowseActivity(): HistoryBrowseActivity
    @ContributesAndroidInjector abstract fun contributesLogSettingActivity(): LogSettingActivity
    @ContributesAndroidInjector abstract fun contributeMainActivity(): MainActivity
    @ContributesAndroidInjector abstract fun contributesPreferencesActivity(): PreferencesActivity
    @ContributesAndroidInjector abstract fun contributesQuickWizardListActivity(): QuickWizardListActivity
    @ContributesAndroidInjector abstract fun contributesRequestDexcomPermissionActivity(): RequestDexcomPermissionActivity
    @ContributesAndroidInjector abstract fun contributesSetupWizardActivity(): SetupWizardActivity
    @ContributesAndroidInjector abstract fun contributesSingleFragmentActivity(): SingleFragmentActivity
    @ContributesAndroidInjector abstract fun contributesDefaultProfileActivity(): ProfileHelperActivity

}