package info.nightscout.androidaps.danars.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danars.services.DanaRSService

@Module
@Suppress("unused")
abstract class DanaRSServicesModule {
    @ContributesAndroidInjector abstract fun contributesDanaRSService(): DanaRSService
}