package app.aaps.plugins.insulin.di

import app.aaps.plugins.insulin.InsulinNewFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class InsulinModule {

    @ContributesAndroidInjector abstract fun contributesInsulinNewFragment(): InsulinNewFragment
}