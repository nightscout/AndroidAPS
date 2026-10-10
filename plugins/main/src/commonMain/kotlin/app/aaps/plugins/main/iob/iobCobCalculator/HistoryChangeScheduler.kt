package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Turns history changes (new glucose values, treatments, temporary basals ...) into recalculations.
 *
 * A burst of changes is collected for [debounce] and handed to [run] once, with the oldest timestamp
 * and the flags of all of them merged.
 *
 * Before [run], a calculation that is still running is allowed to finish. [run] stops the running
 * calculation, and a stopped one throws all of its work away. When a full recalculation takes longer
 * than the time between two glucose values (a 1-minute CGM, or a busy phone), every new value stopped
 * it, the next one started again from the oldest data, and none ever finished - so the loop, which
 * runs after a finished calculation, stopped running. Waiting costs at most one run, and the change
 * is then calculated on top of a full cache, which is usually one or two buckets.
 *
 * The change is remembered while waiting, not applied: the running calculation works on a copy of
 * the data and publishes it at the end, and that would overwrite an invalidation done in the meantime
 * (issue #5066).
 *
 * The cost: until the running calculation ends, the cached buckets do not know about the change. IOB
 * "now", which the loop and the bolus wizard use, is not cached and always comes from the database. COB
 * of a carb entry older than the last calculated bucket shows up when the change has run, so at most
 * one calculation later than before - and before, during a full recalculation, there was no cached
 * data at all.
 *
 * If the calculation is still running after [maxWaitForCalculation], [run] stops it as before, so a
 * hanging calculation cannot hold changes back for ever.
 *
 * @param scope where the debounce runs, null while the plugin is stopped
 * @param awaitCalculationIdle waits for the running calculation, true when it ended within the timeout
 * @param onRunFailed called when [run] throws, to drop the data it may have left half invalidated
 * @param run invalidates the cached data from the timestamp and starts the calculation
 */
internal class HistoryChangeScheduler(
    private val aapsLogger: AAPSLogger,
    private val scope: () -> CoroutineScope?,
    private val awaitCalculationIdle: suspend (timeout: Duration) -> Boolean,
    private val onRunFailed: () -> Unit,
    private val debounce: Duration = DEBOUNCE,
    private val maxWaitForCalculation: Duration = MAX_WAIT_FOR_CALCULATION,
    private val run: suspend (oldDataTimestamp: Long, reloadBgData: Boolean, triggeredByNewBG: Boolean) -> Unit
) {

    private class Scheduled(
        val oldDataTimestamp: Long,
        var reloadBgData: Boolean,
        var triggeredByNewBG: Boolean
    )

    private var scheduled: Scheduled? = null
    private var job: Job? = null

    /**
     * Guards [scheduled] and [job]. Held by both the scheduling call and the launched body when it
     * runs, so a new request cannot land while a run is in progress. Not held while the body waits for
     * the debounce or for the running calculation, so requests arriving then merge into it.
     *
     * A coroutine [Mutex], not an `AapsLock`: the body holds this across [run], which suspends. A
     * thread owned lock such as `ReentrantLock`, which is what `AapsLock` is on the JVM, would then be
     * unlocked from whatever thread the coroutine resumed on. That throws
     * `IllegalMonitorStateException`, leaves the lock held for good, and every later call here would
     * block for the rest of the process lifetime. A `Mutex` belongs to the coroutine rather than to a
     * thread, so resuming elsewhere is fine.
     *
     * A `Mutex` is not reentrant. Nothing inside the guarded region calls back into it.
     */
    private val lock = Mutex()

    suspend fun schedule(oldDataTimestamp: Long, reloadBgData: Boolean, triggeredByNewBG: Boolean): Unit = lock.withLock {
        val current = scheduled
        // if there is nothing scheduled or asking reload deeper to the past
        if (current == null || oldDataTimestamp < current.oldDataTimestamp) {
            // cancel waiting task to prevent sending multiple posts
            job?.cancel()
            // merge flags from previously scheduled event
            val data = Scheduled(
                oldDataTimestamp = oldDataTimestamp,
                reloadBgData = reloadBgData || (current?.reloadBgData ?: false),
                triggeredByNewBG = triggeredByNewBG || (current?.triggeredByNewBG ?: false)
            )
            val launched = scope()?.launch {
                delay(debounce)
                if (!awaitCalculationIdle(maxWaitForCalculation))
                    aapsLogger.warn(LTag.AUTOSENS, "Calculation still running after $maxWaitForCalculation, it is stopped for the history change")
                // Only the waiting is cancellable. Without NonCancellable a late cancel could stop this
                // half done, between clearing the TDD cache and rebuilding from it.
                withContext(NonCancellable) {
                    lock.withLock {
                        // A request with an older timestamp, or a drop, can take the lock between our
                        // waiting running out and us getting it. A replacement carries our flags and an
                        // older timestamp, and a drop means a full recalculation is coming, so this run
                        // is not needed any more.
                        if (scheduled !== data) return@withLock
                        try {
                            aapsLogger.debug(LTag.AUTOSENS, "Running newHistoryData")
                            run(data.oldDataTimestamp, data.reloadBgData, data.triggeredByNewBG)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // The invalidation did not finish, so the caches can still hold values built
                            // from the old data. Throw them away instead of letting the loop dose from a
                            // half invalidated cache. Nothing is started here: the next glucose value
                            // rebuilds everything through the normal path.
                            aapsLogger.error(LTag.AUTOSENS, "newHistoryData failed, dropping all cached data", e)
                            onRunFailed()
                        } finally {
                            // Clear only what this run owns, so the guard above can never stay stuck on
                            // an entry with no job behind it (issue #5066).
                            if (scheduled === data) {
                                scheduled = null
                                job = null
                            }
                        }
                    }
                }
            }
            // Publish only when something is really scheduled. With a stopped plugin scope is null, and
            // a scheduled entry with no runner behind it would wedge the guard above in the same way.
            if (launched == null) {
                aapsLogger.error(LTag.AUTOSENS, "Plugin is stopped, history data change dropped")
            } else {
                scheduled = data
                job = launched
            }
        } else {
            // asked reload is newer -> adjust params only
            if (!current.reloadBgData) current.reloadBgData = reloadBgData
            if (!current.triggeredByNewBG) current.triggeredByNewBG = triggeredByNewBG
        }
    }

    /**
     * Forgets the change that is waiting, if any.
     *
     * For a stopping plugin, and for a full recalculation, which covers every change anyway. It has to
     * clear [scheduled] and not only cancel [job]: a cancelled wait leaves the entry set, and the guard
     * in [schedule] would then merge every later change into an entry nobody runs.
     */
    suspend fun drop(): Unit = lock.withLock {
        job?.cancel()
        job = null
        scheduled = null
    }

    companion object {

        /** How long a burst of history changes is collected before one recalculation runs. */
        val DEBOUNCE = 5.seconds

        /** How long a change waits for a running calculation before it stops it after all. */
        val MAX_WAIT_FOR_CALCULATION = 10.minutes
    }
}
