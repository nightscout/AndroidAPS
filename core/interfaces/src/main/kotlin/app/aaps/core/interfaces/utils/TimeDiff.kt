package app.aaps.core.interfaces.utils

/**
 * A duration split into whole calendar-style components.
 *
 * Replaces the `Map<java.util.concurrent.TimeUnit, Long>` this used to be returned as. Two reasons,
 * beyond `TimeUnit` being a JVM type that cannot go to commonMain:
 *
 * - every lookup was `diff[TimeUnit.DAYS] ?: 0`, which reads as if the key might be missing. It never
 *   was - the map was always built with all seven entries - so the elvis was dead code that hid a
 *   guarantee rather than expressing it.
 * - only [days] and [hours] are actually read anywhere. The rest stay because they cost nothing and
 *   dropping them would be a separate decision.
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
