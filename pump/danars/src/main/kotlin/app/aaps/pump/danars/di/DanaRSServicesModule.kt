package app.aaps.pump.danars.di

import app.aaps.pump.danars.services.DanaRSService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class DanaRSServicesModule {

    @ContributesAndroidInjector abstract fun contributesDanaRSService(): DanaRSService
}