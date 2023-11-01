package app.aaps.helpers

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allow waiting for RX event
 *
 * @property listen Register class for listening
 * @property waitFor Wait until event doesn't appear on bus
 */
@Singleton
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
    fun waitFor(clazz: Class<out Event>, maxSeconds: Long = 20): Pair<Boolean, Event?> {
        val watcher = hashMap[clazz] ?: return Pair(false, null)
        val start = dateUtil.now()
        while (!watcher.get()) {
            if (start + T.secs(maxSeconds).msecs() < dateUtil.now()) {
                aapsLogger.error("${clazz.simpleName} not received")
                return Pair(false, null)
            }
            Thread.sleep(100)
            aapsLogger.debug("Waiting for ${clazz.simpleName}")
        }
        watcher.set(false)
        return Pair(true, eventHashMap[clazz])
    }
}