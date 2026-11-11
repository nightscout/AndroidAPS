package app.aaps.pump.diaconn.di

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.pump.diaconn.emulator.DiaconnEmulatorBleTransport
import app.aaps.pump.diaconn.emulator.DiaconnPumpEmulator
import app.aaps.pump.diaconn.service.DiaconnBleTransportImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** The Bluetooth link of the Diaconn G8. Dana RS binds the plain `BleTransport` already. */
@Qualifier
annotation class DiaconnBle

/**
 * Picks the Diaconn G8 transport: the in-tree emulator when the `emulate_diaconn` option is on, otherwise the real
 * Bluetooth one. The choice is read once, when the graph is built.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DiaconnTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
    @DiaconnBle
    fun provideBleTransport(
        config: Config,
        diaconnBleTransportImpl: DiaconnBleTransportImpl,
        aapsLogger: AAPSLogger,
        pumpEmulators: PumpEmulators
    ): BleTransport =
        if (config.isEnabled(ExternalOptions.EMULATE_DIACONN)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "Diaconn G8 emulator active - real Bluetooth is not used")
            // The same pump after a test restarts the app: it runs on, as a real one would. The radio is new.
            DiaconnEmulatorBleTransport(
                emulator = pumpEmulators.getOrCreate(DiaconnPumpEmulator.KEY) { DiaconnPumpEmulator(aapsLogger = aapsLogger) },
                aapsLogger = aapsLogger
            )
        } else {
            diaconnBleTransportImpl
        }
}
