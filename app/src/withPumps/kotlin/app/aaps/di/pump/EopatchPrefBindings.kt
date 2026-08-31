package app.aaps.di.pump

import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.TempBasalManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The eopatch value objects, on Metro. Was `EopatchPrefModule`.
 *
 * Plain no-argument state holders - they were on the Dagger side only because the whole driver was.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object EopatchPrefBindings {

    @Provides @SingleIn(AppScope::class) fun providePatchConfig(): PatchConfig = PatchConfig()

    @Provides @SingleIn(AppScope::class) fun provideNormalBasalManager(): NormalBasalManager = NormalBasalManager()

    @Provides @SingleIn(AppScope::class) fun provideTempBasalManager(): TempBasalManager = TempBasalManager()

    @Provides @SingleIn(AppScope::class) fun provideAlarms(): Alarms = Alarms()
}
