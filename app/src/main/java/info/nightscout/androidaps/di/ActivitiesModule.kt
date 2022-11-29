package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.activities.HistoryBrowseActivity
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.configuration.activities.SingleFragmentActivity
import info.nightscout.configuration.maintenance.activities.LogSettingActivity

@Module
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesHistoryBrowseActivity(): HistoryBrowseActivity
    @ContributesAndroidInjector abstract fun contributesLogSettingActivity(): LogSettingActivity
    @ContributesAndroidInjector abstract fun contributesMainActivity(): MainActivity
    @ContributesAndroidInjector abstract fun contributesPreferencesActivity(): PreferencesActivity
    @ContributesAndroidInjector abstract fun contributesSingleFragmentActivity(): SingleFragmentActivity

}