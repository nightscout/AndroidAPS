package info.nightscout.pump.common.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.Module
import dagger.Provides
import info.nightscout.pump.common.sync.PumpSyncStorage
import javax.inject.Singleton

@Module
@Suppress("unused")
class PumpCommonModule {

    @Provides
    @Singleton
    fun providesPumpSyncStorage(
        pumpSync: PumpSync,
        sp: SP,
        aapsLogger: AAPSLogger
    ): PumpSyncStorage {
        return PumpSyncStorage(pumpSync, sp, aapsLogger)
    }

}