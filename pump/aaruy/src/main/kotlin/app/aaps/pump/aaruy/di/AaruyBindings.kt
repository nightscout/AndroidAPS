package app.aaps.pump.aaruy.di

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.aaruy.comm.AaruyBleTransport
import app.aaps.pump.aaruy.comm.AaruyBleTransportImpl
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.services.AaruyService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object AaruyBindings {

    /** The emulator when the `emulate_aaruy` file is in the AAPS extra folder, otherwise the radio. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideTransport(context: Context, config: Config, aapsLogger: AAPSLogger): AaruyBleTransport =
        if (config.isEnabled(ExternalOptions.EMULATE_AARUY)) {
            aapsLogger.warn(LTag.PUMPEMULATOR, "Aaruy pump EMULATOR is active. No insulin is delivered.")
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() }))
        } else {
            AaruyBleTransportImpl(context, aapsLogger)
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(context: Context): AaruyHistoryDatabase = AaruyHistoryDatabase.build(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryDailyUnitInfoDao(database: AaruyHistoryDatabase): HistoryDailyUnitInfoDao = database.historyDailyUnitInfoDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHistoryUnitInfoDao(database: AaruyHistoryDatabase): HistoryUnitInfoDao = database.historyUnitInfoDao()

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AaruyService::class)
    fun bindAaruyService(injector: MembersInjector<AaruyService>): MembersInjector<*> = injector
}
