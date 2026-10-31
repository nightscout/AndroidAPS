package app.aaps.core.data.model

/**
 * The end of an entry that starts at [timestamp] and runs for [duration] milliseconds.
 *
 * Stops at [Long.MAX_VALUE] instead of overflowing. A scene without a duration stores its temporary
 * target and therapy event with duration [Long.MAX_VALUE] until the scene ends, and a plain
 * `timestamp + duration` then wraps to a negative number, so the entry looked like it had ended long
 * ago. An end of [Long.MAX_VALUE] means "no end".
 */
fun endOf(timestamp: Long, duration: Long): Long {
    val end = timestamp + duration
    return if (duration > 0 && end < timestamp) Long.MAX_VALUE else end
}

/**
 * The entry of this list that is running at [time], picked the way the "active at" database queries
 * pick it (`getTemporaryBasalActiveAt`, `getExtendedBolusActiveAt`, `getTemporaryTargetActiveAt`): it has
 * started at or before [time], has not ended yet (`timestamp + duration > time`), and of several such
 * entries the one that started last wins. An entry without an end (see [endOf]) is running.
 *
 * For code that walks a range step by step: read the entries of the range from the database once, then
 * ask this for every step, instead of one query per step. The list must be sorted by timestamp, oldest
 * first, as the "starting from time" queries return it.
 *
 * @param duration the duration of an entry in milliseconds
 */
fun <T : TimeStamped> List<T>.latestRunningAt(time: Long, duration: (T) -> Long): T? =
    lastOrNull { it.timestamp <= time && endOf(it.timestamp, duration(it)) > time }
