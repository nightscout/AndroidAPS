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

    /**
     * True while a worker for the slot is enqueued (running or pending) and has not yet drained.
     * Gates [putAndEnqueue] so the WorkManager chain never grows past 1 running + 1 pending worker,
     * regardless of how fast producers call (e.g. xDrip's one-broadcast-per-minute cadence). Without
     * this gate every produce appended a fresh worker, accumulating dozens of BLOCKED workers that
     * all cascade-fail if the chain head is cancelled.
     */
    private val hasPendingWork = HashMap<Inbox<*>, Boolean>()

    @Synchronized
    private fun <T : Any> putInternal(slot: Inbox<T>, value: T) {
        queues.getOrPut(slot) { mutableListOf() }.add(value)
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> drain(slot: Inbox<T>): List<T> {
        // Cleared first so a produce arriving after this point re-enqueues a fresh worker for the
        // values it adds, rather than relying on the current (about-to-finish) run to pick them up.
        hasPendingWork[slot] = false
        return (queues.remove(slot) as List<T>?) ?: emptyList()
    }

    /**
     * Append [value] to [slot]'s queue and ensure a worker is enqueued to drain it.
     *
     * Multiple rapid calls coalesce: while one worker run is processing, later
     * calls add to the queue and the next worker run drains them as a batch. The
     * [hasPendingWork] gate keeps the WorkManager chain at no more than 1 running +
     * 1 pending worker — a fast producer (e.g. one broadcast per minute) can no
     * longer pile up dozens of BLOCKED workers.
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
        enqueueWorker(slot)
    }

    /**
     * Re-insert [values] at the FRONT of [slot]'s queue (preserving their original
     * order ahead of anything produced since the drain) and ensure a worker is
     * enqueued to retry them.
     *
     * A worker drains destructively, so a run that is cancelled mid-batch (e.g.
     * WorkManager stopping it under load) would otherwise lose every value it had
     * already taken but not yet committed. The worker calls this with its unprocessed
     * remainder before propagating the cancellation, so no value is dropped — the
     * next run picks them up. Only use for transient failures (cancellation); a value
     * that deterministically fails processing must be discarded by the consumer to
     * avoid an infinite retry loop.
     */
    @Synchronized
    fun <T : Any> requeue(slot: Inbox<T>, values: List<T>) {
        if (values.isEmpty()) return
        queues.getOrPut(slot) { mutableListOf() }.addAll(0, values)
        enqueueWorker(slot)
    }

    private fun enqueueWorker(slot: Inbox<*>) {
        if (hasPendingWork[slot] == true) return
        hasPendingWork[slot] = true
        WorkManager.getInstance(context).enqueueUniqueWork(
            slot.workName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(slot.workerClass).build()
        )
    }
}
