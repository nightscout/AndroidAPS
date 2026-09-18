package app.aaps.pump.danars.di

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.pump.dana.di.DanaAccessors
import app.aaps.pump.danars.DanaRSPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Dana RS driver - see [DanaAccessors] for why these are read
 * from the app's graph rather than built by the test, and what contributing them costs.
 *
 * [bleTransport] is a core type but a Dana binding: [DanaRSTransportBindings] decides between the real
 * transport and the emulator, so this is where a test can see which one it got.
 */
@ContributesTo(AppScope::class)
interface DanaRSAccessors : DanaAccessors {

    val danaRSPlugin: DanaRSPlugin
    val bleTransport: BleTransport
}
