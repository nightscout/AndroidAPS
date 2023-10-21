package com.microtechmd.equil.di

import com.microtechmd.equil.manager.EquilManager
import com.microtechmd.equil.service.EquilService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class EquilServicesModule {

    @ContributesAndroidInjector abstract fun contributesEquilService(): EquilService
    @ContributesAndroidInjector abstract fun contributesEquilManager(): EquilManager
    // @ContributesAndroidInjector abstract fun contributesBolusProfile(): BolusProfile

}