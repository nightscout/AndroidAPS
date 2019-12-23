package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment

@Module
abstract class FragmentsModule {
    @ContributesAndroidInjector
    abstract fun contributesPreferencesFragment(): MyPreferenceFragment
}