package app.aaps.di.pump

import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasalManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provided from `:app` so `:pump:eopatch` needs no Dagger processor. */
@Module
@InstallIn(SingletonComponent::class)
class EopatchPrefModule {

    @Provides @Singleton fun providePatchConfig(): PatchConfig = PatchConfig()

    @Provides @Singleton fun provideNormalBasalManager(): NormalBasalManager = NormalBasalManager()

    @Provides @Singleton fun provideTempBasalManager(): TempBasalManager = TempBasalManager()

    @Provides @Singleton fun provideAlarms(): Alarms = Alarms()
}
