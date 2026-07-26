package app.aaps.pump.common.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.sync.PumpSyncStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class PumpCommonModule {

    @Provides
    @Singleton
    fun providesPumpSyncStorage(
        pumpSync: PumpSync,
        preferences: Preferences,
        aapsLogger: AAPSLogger
    ): PumpSyncStorage = PumpSyncStorage(pumpSync, preferences, aapsLogger)
}