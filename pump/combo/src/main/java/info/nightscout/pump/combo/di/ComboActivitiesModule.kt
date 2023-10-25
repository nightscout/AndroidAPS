package info.nightscout.pump.combo.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.combo.ComboFragment

@Module
@Suppress("unused")
abstract class ComboActivitiesModule {
    @ContributesAndroidInjector abstract fun contributesComboFragment(): ComboFragment
}