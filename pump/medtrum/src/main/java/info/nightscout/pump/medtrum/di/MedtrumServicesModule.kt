package info.nightscout.pump.medtrum.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.medtrum.services.MedtrumService

@Module
@Suppress("unused")
abstract class MedtrumServicesModule {
    @ContributesAndroidInjector abstract fun contributesDanaRSService(): MedtrumService
}