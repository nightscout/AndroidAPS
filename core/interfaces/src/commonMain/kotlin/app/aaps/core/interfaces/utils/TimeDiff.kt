package app.aaps.core.interfaces.utils

/**
 * A duration split into whole calendar-style components.
 *
 * Every component is always present, so a caller reads [days] or [hours] directly rather than
 * guarding for a missing one. Only those two are read anywhere today; the rest stay because they
 * cost nothing.
 */
data class TimeDiff(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val milliseconds: Long,
    val microseconds: Long,
    val nanoseconds: Long
)
