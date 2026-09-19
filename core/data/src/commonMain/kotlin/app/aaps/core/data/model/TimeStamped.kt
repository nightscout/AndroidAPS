package app.aaps.core.data.model

/**
 * Marker interface for entities that represent a time-stamped event.
 * Allows generic time-based operators (e.g. clock-skew compensation) to
 * be expressed without per-call-site lambdas.
 */
interface TimeStamped {

    /** Event time in milliseconds since the epoch. */
    val timestamp: Long
}
