package app.aaps.plugins.main.di

import app.aaps.plugins.main.general.food.FoodFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class FoodModule {

    @ContributesAndroidInjector abstract fun contributesFoodFragment(): FoodFragment
}