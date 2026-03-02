package app.aaps.pump.danars.di

import app.aaps.pump.danars.services.DanaRSService
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class DanaRSServicesModule {

    @ContributesAndroidInjector abstract fun contributesDanaRSService(): DanaRSService
}