package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.activities.*
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansLoginActivity
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.smsCommunicator.activities.SmsCommunicatorOtpActivity
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
    @ContributesAndroidInjector abstract fun contributesSmsCommunicatorOtpActivity(): SmsCommunicatorOtpActivity
    @ContributesAndroidInjector abstract fun contributesStatsActivity(): StatsActivity
    @ContributesAndroidInjector abstract fun contributesSurveyActivity(): SurveyActivity
    @ContributesAndroidInjector abstract fun contributesDefaultProfileActivity(): ProfileHelperActivity
    @ContributesAndroidInjector abstract fun contributesOpenHumansLoginActivity(): OpenHumansLoginActivity

}