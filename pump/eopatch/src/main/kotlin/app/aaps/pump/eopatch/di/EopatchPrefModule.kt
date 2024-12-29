package app.aaps.pump.eopatch.di

import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasalManager
import dagger.Module
import dagger.Provides
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


