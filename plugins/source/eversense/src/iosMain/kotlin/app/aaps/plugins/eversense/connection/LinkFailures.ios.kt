package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.pump.ble.BleFailure

/** `CBError.peripheralDisconnected` (7): the transmitter ended the connection. */
internal actual fun closedByTransmitter(failure: BleFailure): Boolean = failure.platformStatus == 7

/** iOS suspends the app in the background: a lost link waits for the transmitter in a pending connect instead. */
internal actual val reconnectWithoutTimers: Boolean = true
