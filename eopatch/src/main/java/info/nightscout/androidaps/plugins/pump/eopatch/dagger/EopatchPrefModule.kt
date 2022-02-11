package info.nightscout.androidaps.plugins.pump.eopatch.dagger

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.pump.eopatch.ble.*
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasalManager
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasalManager
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Singleton

@Module
class EopatchPrefModule {
    @Provides
    @Singleton
    internal fun providePatchConfig(): PatchConfig {
        return PatchConfig()
    }

    @Provides
    @Singleton
    internal fun provideNormalBasalManager(sp: SP): NormalBasalManager {
        return NormalBasalManager()
    }

    @Provides
    @Singleton
    internal fun provideTempBasalManager(sp: SP): TempBasalManager {
        return TempBasalManager()
    }

    @Provides
    @Singleton
    internal fun provideAlarms(): Alarms {
        return Alarms()
    }
}


