package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.pump.ble.BleFailure

/** The desktop has no Bluetooth link to a transmitter. */
internal actual fun closedByTransmitter(failure: BleFailure): Boolean = false

internal actual val reconnectWithoutTimers: Boolean = false
