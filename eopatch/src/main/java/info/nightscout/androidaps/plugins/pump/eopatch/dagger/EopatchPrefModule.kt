package info.nightscout.androidaps.plugins.pump.eopatch.dagger

import dagger.Module
import dagger.Provides
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasalManager
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasalManager
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
    internal fun provideNormalBasalManager(): NormalBasalManager {
        return NormalBasalManager()
    }

    @Provides
    @Singleton
    internal fun provideTempBasalManager(): TempBasalManager {
        return TempBasalManager()
    }

    @Provides
    @Singleton
    internal fun provideAlarms(): Alarms {
        return Alarms()
    }
}


