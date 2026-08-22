package app.aaps.helpers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.utils.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import javax.inject.Inject

/**
 * Allow waiting for RX event
 *
 * @property listen Register class for listening
 * @property waitFor Wait until event doesn't appear on bus
 */
class RxHelper @Inject constructor(
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) {

    private val hashMap = HashMap<KClass<out Event>, AtomicBoolean>()
    private val eventHashMap = HashMap<KClass<out Event>, Event>()

    // Lives as long as the helper; clear() cancels its collectors, like clearing the CompositeDisposable.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Register class for listening
     *
     * @param clazz Class to observe
     * @return AtomicBoolean trigger
     */
    fun listen(clazz: KClass<out Event>): AtomicBoolean =
        hashMap[clazz] ?: AtomicBoolean(false).also { ab ->
            hashMap[clazz] = ab
            // Setup RxBus tracking. UNDISPATCHED because RxBus has no replay: a test that sends an
            // event right after listen() returns must not race the collector starting.
            rxBus.toFlow(clazz)
                .collectResilient(scope, aapsLogger, LTag.EVENTS, start = CoroutineStart.UNDISPATCHED) {
                    aapsLogger.info(LTag.EVENTS, "==>> ${clazz.simpleName} registered")
                    ab.set(true)
                    eventHashMap[clazz] = it
                }
        }

    /**
     * Wait until event doesn't appear on bus
     *
     * @param clazz Class to observe
     * @param maxSeconds max waiting time in seconds
     */
    fun waitFor(clazz: KClass<out Event>, maxSeconds: Long = 40, comment: String = ""): Pair<Boolean, Event?> {
        val watcher = hashMap[clazz] ?: error("Class not registered ${clazz.simpleName}")
        val start = dateUtil.now()
        while (!watcher.get()) {
            if (start + T.secs(maxSeconds).msecs() < dateUtil.now()) {
                aapsLogger.error("${clazz.simpleName} not received $comment")
                return Pair(false, null)
            }
            Thread.sleep(100)
            aapsLogger.debug("Waiting for ${clazz.simpleName} $comment")
        }
        aapsLogger.info(LTag.EVENTS, "Received ${clazz.simpleName} $comment ${eventHashMap[clazz]}")
        watcher.set(false)
        return Pair(true, eventHashMap[clazz])
    }

    /**
     * Reset receiver to wait for new event
     *
     * @param clazz Class
     */
    fun resetState(clazz: KClass<out Event>) {
        hashMap[clazz]?.set(false)
        eventHashMap.remove(clazz)
    }

    /**
     * Wait until condition is met by polling
     *
     * @param comment Description for logging
     * @param maxSeconds max waiting time in seconds
     * @param condition Condition to check
     */
    fun waitUntil(comment: String = "", maxSeconds: Long = 40, condition: () -> Boolean): Boolean {
        val start = dateUtil.now()
        while (!condition()) {
            if (start + T.secs(maxSeconds).msecs() < dateUtil.now()) {
                aapsLogger.error("Condition not met: $comment")
                return false
            }
            Thread.sleep(100)
            aapsLogger.debug("Waiting for condition: $comment")
        }
        aapsLogger.info(LTag.EVENTS, "Condition met: $comment")
        return true
    }

    fun clear() {
        // Cancels the running collectors but keeps the scope usable, the way CompositeDisposable.clear()
        // left its container usable. A plain scope.cancel() would make every later listen() do nothing.
        scope.coroutineContext.cancelChildren()
    }
}