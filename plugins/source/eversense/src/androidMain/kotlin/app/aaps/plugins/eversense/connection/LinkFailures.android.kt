package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.pump.ble.BleFailure

/** GATT status 19, `GATT_CONN_TERMINATE_PEER_USER`: an E3 lifted off the sensor, or the usual end of a 365 cycle. */
internal actual fun closedByTransmitter(failure: BleFailure): Boolean = failure.platformStatus == 19

/** Android keeps running in the background: retries wait with timers. */
internal actual val reconnectWithoutTimers: Boolean = false
