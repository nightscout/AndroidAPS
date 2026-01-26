package app.aaps.plugins.insulin.di

import app.aaps.plugins.insulin.InsulinNewFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class InsulinModule {

    @ContributesAndroidInjector abstract fun contributesInsulinNewFragment(): InsulinNewFragment
}