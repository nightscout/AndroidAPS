package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.general.food.FoodFragment

@Module
@Suppress("unused")
abstract class FoodModule {

    @ContributesAndroidInjector abstract fun contributesFoodFragment(): FoodFragment
}