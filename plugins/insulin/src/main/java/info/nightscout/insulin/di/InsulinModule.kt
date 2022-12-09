package info.nightscout.insulin.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.insulin.InsulinFragment

@Module
@Suppress("unused")
abstract class InsulinModule {

    @ContributesAndroidInjector abstract fun contributesInsulinFragment(): InsulinFragment
}