package app.aaps.di.pump

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.ble.CarelevoBleTransportImpl
import app.aaps.pump.carelevo.emulator.CarelevoEmulatorBleTransport
import app.aaps.pump.carelevo.emulator.CarelevoPumpEmulator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Picks the CareLevo transport: the in-tree emulator when the `emulate_carelevo` marker file is
 * present, otherwise the real BLE one. That way the driver can be exercised end-to-end without
 * patch hardware.
 *
 * `CarelevoBleTransportImpl` carries `@SingleIn(AppScope::class)` but no `@ContributesBinding` - the
 * binding for `CarelevoBleTransport` has to be this function, or the emulator branch would be
 * bypassed.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object CarelevoTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideCarelevoBleTransport(
        config: Config,
        carelevoBleTransportImpl: CarelevoBleTransportImpl,
        aapsLogger: AAPSLogger
    ): CarelevoBleTransport =
        if (config.isEnabled(ExternalOptions.EMULATE_CARELEVO)) {
            aapsLogger.debug(LTag.PUMPEMULATOR, "CareLevo emulator active - real Bluetooth is not used")
            CarelevoEmulatorBleTransport(
                emulator = CarelevoPumpEmulator(aapsLogger = aapsLogger),
                aapsLogger = aapsLogger
            )
        } else {
            carelevoBleTransportImpl
        }
}
