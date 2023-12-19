package app.aaps.pump.equil.di

import app.aaps.pump.equil.manager.EquilManager
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class EquilServicesModule {

    @ContributesAndroidInjector abstract fun contributesEquilManager(): EquilManager
}