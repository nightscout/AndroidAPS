package app.aaps.helpers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.atomic.AtomicBoolean
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

    private val hashMap = HashMap<Class<out Event>, AtomicBoolean>()
    private val eventHashMap = HashMap<Class<out Event>, Event>()
    private val disposable = CompositeDisposable()

    /**
     * Register class for listening
     *
     * @param clazz Class to observe
     * @return AtomicBoolean trigger
     */
    fun listen(clazz: Class<out Event>): AtomicBoolean =
        hashMap[clazz] ?: AtomicBoolean(false).also { ab ->
            hashMap[clazz] = ab
            // Setup RxBus tracking
            disposable += rxBus
                .toObservable(clazz)
                .observeOn(aapsSchedulers.io)
                .subscribe({
                               aapsLogger.info(LTag.EVENTS, "==>> ${clazz.simpleName} registered")
                               ab.set(true)
                               eventHashMap[clazz] = it
                           }, fabricPrivacy::logException)
        }

    /**
     * Wait until event doesn't appear on bus
     *
     * @param clazz Class to observe
     * @param maxSeconds max waiting time in seconds
     */
    fun waitFor(clazz: Class<out Event>, maxSeconds: Long = 40, comment: String = ""): Pair<Boolean, Event?> {
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
    fun resetState(clazz: Class<out Event>) {
        hashMap[clazz]?.set(false)
        eventHashMap.remove(clazz)
    }

    fun clear() {
        disposable.clear()
    }
}