package app.aaps.pump.diaconn.di

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.diaconn.DiaconnG8Plugin
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Diaconn G8 driver, read from the app's own graph. Declared here and not in
 * `:app`, so that removing `:pump:diaconn` from `settings.gradle` takes the accessors with it (see `DanaAccessors`).
 */
@ContributesTo(AppScope::class)
interface DiaconnAccessors {

    /** The real Bluetooth link, or the emulator when the `emulate_diaconn` option is on. */
    @DiaconnBle val diaconnBleTransport: BleTransport

    val diaconnG8Pump: DiaconnG8Pump
    val diaconnG8Plugin: DiaconnG8Plugin
}
