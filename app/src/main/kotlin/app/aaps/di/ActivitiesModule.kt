package app.aaps.di

import app.aaps.MainActivity
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.MyPreferenceFragment
import app.aaps.activities.PreferencesActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesHistoryBrowseActivity(): HistoryBrowseActivity
    @ContributesAndroidInjector abstract fun contributesMainActivity(): MainActivity
    @ContributesAndroidInjector abstract fun contributesPreferencesActivity(): PreferencesActivity
    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment
}