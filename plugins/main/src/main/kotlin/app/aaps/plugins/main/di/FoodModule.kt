package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.food.FoodFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class FoodModule {

    @ContributesAndroidInjector abstract fun contributesFoodFragment(): FoodFragment
}