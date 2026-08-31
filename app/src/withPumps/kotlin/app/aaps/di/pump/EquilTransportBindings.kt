package app.aaps.di.pump

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.ble.EquilBleTransportImpl
import app.aaps.pump.equil.emulator.EquilEmulatorBleTransport
import app.aaps.pump.equil.emulator.EquilPumpEmulator
import app.aaps.pump.equil.keys.EquilStringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Picks the Equil transport: the in-tree emulator when that option is on, otherwise the real BLE one.
 *
 * Metro's, not Dagger's, for the same reason as [DanaTransportBindings]: the transport is what the
 * driver talks to, and the driver is Metro owned. A Dagger copy here would leave the emulator tests and
 * the running driver on two different transports.
 *
 * `EquilBleTransportImpl` carries `@SingleIn(AppScope::class)` but no `@ContributesBinding` - the
 * binding for `EquilBleTransport` has to be this function, or the emulator branch would be bypassed.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object EquilTransportBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideEquilBleTransport(
        config: Config,
        equilBleTransportImpl: EquilBleTransportImpl,
        aapsLogger: AAPSLogger,
        preferences: Preferences
    ): EquilBleTransport {
        return if (config.isEnabled(ExternalOptions.EMULATE_EQUIL)) {
            val pumpEmulator = EquilPumpEmulator(aapsLogger = aapsLogger)
            EquilEmulatorBleTransport(
                emulator = pumpEmulator,
                aapsLogger = aapsLogger,
                serialNumberProvider = {
                    val sn = "A${String.format("%05d", (0..99999).random())}"
                    preferences.put(EquilStringKey.EmulatorDeviceName, "Equil - $sn")
                    aapsLogger.debug(LTag.PUMPCOMM, "Equil emulator scan with serial: $sn")
                    sn
                },
                storedPasswordProvider = {
                    // Read the device password stored by CmdPair after pairing
                    val pwd = preferences.get(EquilStringKey.Password)
                    if (pwd.isNotEmpty()) pwd else null
                }
            )
        } else {
            equilBleTransportImpl
        }
    }
}
