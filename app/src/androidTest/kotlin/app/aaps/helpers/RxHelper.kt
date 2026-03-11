package app.aaps.helpers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Allow waiting for RX event
 *
 * @property listen Register class for listening
 * @property waitFor Wait until event doesn't appear on bus
 */
class RxHelper @Inject constructor(
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    private data class QueuedEvent(
        val sequence: Long,
        val event: Event
    )

    private data class Watcher(
        val triggered: AtomicBoolean,
        val events: BlockingQueue<QueuedEvent>
    )

    private val watchers = HashMap<Class<out Event>, Watcher>()
    private val lastSequences = HashMap<Class<out Event>, Long>()
    private val sequence = AtomicLong(0)
    private val disposable = CompositeDisposable()

    /**
     * Register class for listening
     *
     * @param clazz Class to observe
     * @return AtomicBoolean trigger
     */
    fun listen(clazz: Class<out Event>): AtomicBoolean =
        watchers[clazz]?.triggered ?: AtomicBoolean(false).also { ab ->
            val queue: BlockingQueue<QueuedEvent> = LinkedBlockingQueue()
            watchers[clazz] = Watcher(ab, queue)
            // Setup RxBus tracking
            disposable += rxBus
                .toObservable(clazz)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                               aapsLogger.info(LTag.EVENTS, "==>> ${clazz.simpleName} registered")
                               watchers[clazz]?.let { watcher ->
                                   watcher.triggered.set(true)
                                   watcher.events.add(QueuedEvent(sequence.incrementAndGet(), it))
                               }
                           }, fabricPrivacy::logException)
        }

    /**
     * Wait until event doesn't appear on bus
     *
     * @param clazz Class to observe
     * @param maxSeconds max waiting time in seconds
     */
    fun waitFor(clazz: Class<out Event>, maxSeconds: Long = 40, comment: String = ""): Pair<Boolean, Event?> {
        val watcher = watchers[clazz] ?: error("Class not registered ${clazz.simpleName}")
        val queuedEvent = try {
            watcher.events.poll(maxSeconds, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
        if (queuedEvent == null) {
            aapsLogger.error("${clazz.simpleName} not received $comment")
            return Pair(false, null)
        }
        lastSequences[clazz] = queuedEvent.sequence
        aapsLogger.info(LTag.EVENTS, "Received ${clazz.simpleName} $comment ${queuedEvent.event}")
        watcher.triggered.set(false)
        return Pair(true, queuedEvent.event)
    }

    fun waitForAfter(
        clazz: Class<out Event>,
        minSequence: Long,
        maxSeconds: Long = 40,
        comment: String = ""
    ): Pair<Boolean, Event?> {
        val watcher = watchers[clazz] ?: error("Class not registered ${clazz.simpleName}")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(maxSeconds)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) {
                aapsLogger.error("${clazz.simpleName} not received $comment")
                return Pair(false, null)
            }
            val queuedEvent = try {
                watcher.events.poll(remaining, TimeUnit.NANOSECONDS)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } ?: continue
            lastSequences[clazz] = queuedEvent.sequence
            if (queuedEvent.sequence > minSequence) {
                aapsLogger.info(LTag.EVENTS, "Received ${clazz.simpleName} $comment ${queuedEvent.event}")
                watcher.triggered.set(false)
                return Pair(true, queuedEvent.event)
            }
        }
    }

    /**
     * Reset receiver to wait for new event
     *
     * @param clazz Class
     */
    fun resetState(clazz: Class<out Event>) {
        watchers[clazz]?.let { watcher ->
            watcher.triggered.set(false)
            watcher.events.clear()
        }
        lastSequences.remove(clazz)
    }

    fun clear() {
        disposable.clear()
        watchers.clear()
        lastSequences.clear()
    }

    fun lastSequence(clazz: Class<out Event>): Long? = lastSequences[clazz]

    fun currentSequence(): Long = sequence.get()
    }
}
