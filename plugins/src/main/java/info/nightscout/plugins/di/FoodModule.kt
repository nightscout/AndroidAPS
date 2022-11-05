package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.food.FoodFragment
import info.nightscout.plugins.general.food.FoodPlugin

@Module
@Suppress("unused")
abstract class FoodModule {

    @ContributesAndroidInjector abstract fun contributesFoodFragment(): FoodFragment
    @ContributesAndroidInjector abstract fun contributesFoodWorker(): FoodPlugin.FoodWorker
}