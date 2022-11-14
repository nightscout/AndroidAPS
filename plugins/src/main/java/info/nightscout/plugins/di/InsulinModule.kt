package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.insulin.InsulinFragment

@Module
@Suppress("unused")
abstract class InsulinModule {

    @ContributesAndroidInjector abstract fun contributesInsulinFragment(): InsulinFragment
}