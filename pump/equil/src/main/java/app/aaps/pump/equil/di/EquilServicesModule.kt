package app.aaps.pump.equil.di

import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.service.EquilService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class EquilServicesModule {

    @ContributesAndroidInjector abstract fun contributesEquilService(): EquilService
    @ContributesAndroidInjector abstract fun contributesEquilManager(): EquilManager
    // @ContributesAndroidInjector abstract fun contributesBolusProfile(): BolusProfile

}