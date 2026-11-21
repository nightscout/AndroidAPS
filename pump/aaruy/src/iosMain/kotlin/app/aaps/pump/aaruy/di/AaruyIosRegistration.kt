package app.aaps.pump.aaruy.di

import app.aaps.pump.aaruy.compose.AaruyBleReadyCheck
import app.aaps.pump.aaruy.compose.IosAaruyBleReadyCheck
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.pump.aaruy.database.buildAaruyHistoryDatabase
import app.aaps.pump.aaruy.emulator.AaruyEmulatorPeripheral
import app.aaps.pump.aaruy.services.AaruyEmulatorTriggers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * iOS has every port of Aaruy (see [AaruyRegistration]), so Aaruy is a pump the user can select here. The system pairs by
 * itself and asks the user for the PIN, which the pairing screen shows. Not yet run with a real pump on an iPhone.
 */
@ContributesTo(AppScope::class)
@BindingContainer(includes = [AaruyRegistration::class])
object AaruyIosRegistration {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(): AaruyHistoryDatabase = buildAaruyHistoryDatabase()

    /** No adb on an iPhone: the emulated pump is driven by the tests only. */
    @Provides
    fun provideEmulatorTriggers(): AaruyEmulatorTriggers = object : AaruyEmulatorTriggers {
        override fun start(emulator: AaruyEmulatorPeripheral) = Unit
    }

    @Provides
    fun provideBleReadyCheck(check: IosAaruyBleReadyCheck): AaruyBleReadyCheck = check
}
