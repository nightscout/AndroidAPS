package app.aaps.core.data.model

/**
 * The entry of this list that is running at [time], picked the way the "active at" database queries
 * pick it (`getTemporaryBasalActiveAt`, `getExtendedBolusActiveAt`, `getTemporaryTargetActiveAt`): it has
 * started at or before [time], has not ended yet (`timestamp + duration > time`), and of several such
 * entries the one that started last wins.
 *
 * For code that walks a range step by step: read the entries of the range from the database once, then
 * ask this for every step, instead of one query per step. The list must be sorted by timestamp, oldest
 * first, as the "starting from time" queries return it.
 *
 * @param duration the duration of an entry in milliseconds
 */
fun <T : TimeStamped> List<T>.latestRunningAt(time: Long, duration: (T) -> Long): T? =
    lastOrNull { it.timestamp <= time && it.timestamp + duration(it) > time }
