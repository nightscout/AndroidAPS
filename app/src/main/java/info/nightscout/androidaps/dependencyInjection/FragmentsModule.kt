package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorFragment

@Module
abstract class FragmentsModule {
    @ContributesAndroidInjector
    abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector
    abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
}