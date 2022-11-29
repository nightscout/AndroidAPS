package info.nightscout.pump.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.danars.services.DanaRSService

@Module
@Suppress("unused")
abstract class DanaRSServicesModule {
    @ContributesAndroidInjector abstract fun contributesDanaRSService(): DanaRSService
}