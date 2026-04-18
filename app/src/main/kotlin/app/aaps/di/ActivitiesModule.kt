package app.aaps.di

import app.aaps.MainActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class ActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesMainActivity(): MainActivity
}
