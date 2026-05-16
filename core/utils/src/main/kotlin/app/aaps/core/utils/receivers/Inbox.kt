package app.aaps.core.utils.receivers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Type-safe slot for queue-and-drain delivery of payloads to a worker.
 *
 * Each subclass declares a unique [workName] used as the WorkManager unique-work
 * name and a [workerClass] that consumes the queue by calling
 * [DataInbox.drain] on this slot.
 *
 * Slots are typically declared as `object` singletons next to the worker:
 *
 * ```
 * object XdripInbox : Inbox<Bundle>("xdrip-bg", XdripSourceWorker::class.java)
 * ```
 *
 * Callers that produce data invoke [DataInbox.putAndEnqueue]; consumers
 * (workers) read everything currently queued via [DataInbox.drain].
 */
abstract class Inbox<T : Any>(
    val workName: String,
    val workerClass: Class<out ListenableWorker>
)

/**
 * In-process queues keyed by [Inbox] slot. Append-on-put, drain-on-pickup.
 *
 * Bursts auto-coalesce: while a worker is running, additional produces add to
 * the queue; the next-enqueued worker run drains everything that accumulated.
 *
 * Empty after every drain — no orphan accumulation possible as long as every
 * slot has a registered drainer (its [Inbox.workerClass]). A produced value
 * with no drainer is the only way to leak, and that is a wiring bug caught the
 * first time a developer notices a missing-data symptom.
 */
@Singleton
class DataInbox @Inject constructor(
    private val context: Context
) {

    private val queues = HashMap<Inbox<*>, MutableList<Any>>()

    @Synchronized
    private fun <T : Any> putInternal(slot: Inbox<T>, value: T) {
        queues.getOrPut(slot) { mutableListOf() }.add(value)
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> drain(slot: Inbox<T>): List<T> =
        (queues.remove(slot) as List<T>?) ?: emptyList()

    /**
     * Append [value] to [slot]'s queue and enqueue the slot's worker under
     * its [Inbox.workName] unique-work name with `APPEND_OR_REPLACE` policy.
     *
     * Multiple rapid calls coalesce: while one worker run is processing, later
     * calls add to the queue and the next worker run (appended via WorkManager)
     * drains them as a batch.
     *
     * `@Synchronized`: the put + enqueue pair must be atomic against concurrent
     * producers and against a drainer running on the worker thread. Without the
     * outer lock, a fast worker can drain a freshly-put value and finish before
     * a second producer's enqueue lands, with no guarantee of which WorkManager
     * state the second enqueue races against. Lock contention is negligible on
     * this path (broadcast/event cadence).
     */
    @Synchronized
    fun <T : Any> putAndEnqueue(slot: Inbox<T>, value: T) {
        putInternal(slot, value)
        WorkManager.getInstance(context).enqueueUniqueWork(
            slot.workName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(slot.workerClass).build()
        )
    }
}
