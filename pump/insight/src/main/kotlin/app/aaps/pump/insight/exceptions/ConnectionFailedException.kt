package app.aaps.pump.insight.exceptions

/**
 * The Bluetooth socket could not be connected.
 *
 * [cause] is the IOException the platform raised. It used to be dropped, which left every
 * connection failure looking the same in the log - "out of range", "socket already used" and
 * "refused" are very different problems and only the platform message tells them apart.
 */
class ConnectionFailedException(val durationOfConnectionAttempt: Long, cause: Throwable? = null) : InsightException(cause)
