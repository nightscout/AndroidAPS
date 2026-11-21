package app.aaps.plugins.eversense.protocol

import java.time.Clock

/** The tests compare against the frozen first version with a fixed [Clock], as they did before the protocol was shared. */
internal fun <T> PacketTime.withClock(clock: Clock, operation: () -> T): T = withClock({ clock.millis() }, operation)
