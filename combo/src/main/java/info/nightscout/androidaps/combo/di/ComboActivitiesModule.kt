package info.nightscout.androidaps.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.pump.combo.ComboFragment

@Module
@Suppress("unused")
abstract class ComboActivitiesModule {
    @ContributesAndroidInjector abstract fun contributesComboFragment(): ComboFragment
}