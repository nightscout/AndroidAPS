package info.nightscout.androidaps.plugins.pump.common.di

import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncStorage
import info.nightscout.androidaps.utils.sharedPreferences.SP
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