package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.pump.ble.BleFailure

/** Whether the transmitter closed the link itself, rather than the radio losing it. */
internal expect fun closedByTransmitter(failure: BleFailure): Boolean

/**
 * Whether a link lost by radio is connected again at once, with a connect that waits for the transmitter with no time
 * limit. True on iOS: an app in the background is suspended and runs no timers, so a retry that waits on a timer may
 * never come; iOS keeps such a connect open at no cost and wakes the app when the transmitter is back. False on
 * Android, which keeps running and waits with timers between attempts.
 */
internal expect val reconnectWithoutTimers: Boolean
