package app.aaps.wear.complications.cwf

import app.aaps.wear.complications.CwfAmbientBgComplication
import app.aaps.wear.complications.CwfAmbientStatusComplication
import app.aaps.wear.complications.CwfImageComplication

import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.events.EventWearPreferenceChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the system to refresh the Custom watch face image complications when the picture has actually
 * changed, instead of waiting for the periodic timer.
 *
 * `UPDATE_PERIOD_SECONDS` is documented as a wish and was measured being honoured only every 1.5 to
 * 6 minutes, which is far too slow for a watch face. A data source may also **push**, and a push was
 * measured being delivered in **15 to 115 ms** - effectively immediately, and not throttled. So the
 * cadence is ours to choose; see `_docs/CWF_WFF_Prompt.md`.
 *
 * Three triggers, each matching something that genuinely changes the picture:
 *
 * - **the clock**, on a timer, refreshing only the upper half - the half that carries the time;
 * - **new data** from the phone, refreshing both halves;
 * - **a preference change**, refreshing both halves at once, because a preference can change the
 *   layout itself and the user is looking at the watch when they change one.
 *
 * Lives for the life of the process rather than of a service: a data source is bound only for as
 * long as it takes to answer one request, so nothing inside one can drive a schedule.
 */
@Singleton
class CwfComplicationUpdater @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val complicationDataRepository: ComplicationDataRepository,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        /**
         * Refresh rates for the upper half, the half that carries the clock.
         *
         * Both are aligned to a multiple of the interval rather than "now plus interval": the picture
         * contains the watch face's own clock, so landing just *after* a boundary is what keeps the
         * displayed value right. Refreshing every minute at an arbitrary phase would leave the minute
         * wrong for most of each minute, and a second shown off the 0/1/2... grid looks wrong even
         * when it is only a second out.
         */
        private const val SECOND_INTERVAL_MS = 1_000L
        private const val MINUTE_INTERVAL_MS = 60_000L

        /**
         * Slower rates for a watch that cannot keep up with one frame a second.
         *
         * The user's rule for this topic: one second where seconds are shown, falling back to two or
         * five, and always on the interval's own grid. This is that fallback, and it is not
         * theoretical - measured on a Galaxy Watch 4 the upper half takes 576 ms to 2.2 s per frame,
         * where an emulator takes 34 ms. Asking every second there queues requests we cannot answer;
         * the queue then empties in a burst, which the wearer sees as a second hand jumping 30 s,
         * then 2 s, then 5 s. Slower and steady beats nominally-fast and lurching.
         */
        private const val SLOW_INTERVAL_MS = 2_000L
        private const val SLOWEST_INTERVAL_MS = 5_000L

        /**
         * How much of an interval a frame may use before the rate drops.
         *
         * A third, so two thirds stay free for everything else the watch is doing - including the
         * other half of the face and the system's own compositing. At the measured 576 ms a frame
         * this lands on the two second rate; beyond about 1.7 s it drops to five.
         */
        private const val FRAME_BUDGET_FRACTION = 3

        /**
         * How long a burst of triggers is allowed to coalesce into one refresh.
         *
         * Both trigger sources arrive in bursts. The repository is a DataStore flow that emits per
         * field, so one arrival from the phone produces several emissions; and one user action in the
         * settings writes several preference keys, each firing the listener. Refreshing on each of
         * them was measured producing ten renders in two seconds.
         */
        private const val COALESCE_MS = 1_000L

        /**
         * The quiet gap asked of a data change, longer than [COALESCE_MS].
         *
         * The phone writes four times per sync - status, glucose, graph and treatments - and the
         * writes land about a second apart, which is just far enough apart to clear a one second
         * gap one after another. Measured: 13 refreshes in under 3 minutes, each redrawing both
         * halves, for data that arrives roughly once a minute.
         *
         * Waiting a few seconds collapses a sync into a single refresh. The delay costs nothing
         * visible: glucose arrives every 5 minutes, so a picture that appears a few seconds later
         * is still the same picture.
         */
        private const val DATA_COALESCE_MS = 5_000L

        /**
         * How often the debounce above is checked.
         *
         * [COALESCE_MS] is a **trailing** debounce - the refresh waits for that much quiet - not a
         * tumbling window. A fixed window only rate-limits: three arrivals a second apart were
         * measured producing three full refreshes, each redrawing both halves, because each landed
         * in a window of its own. Polling has to be well below the debounce so waiting for quiet
         * does not add a visible delay of its own.
         */
        private const val COALESCE_POLL_MS = 200L

        /**
         * How recently the face must have been asked for before a clock tick skips itself.
         *
         * A data change redraws the face, and the clock loop does not know it happened - so when the
         * two land in the same second the picture was built and compressed twice, work thrown away
         * once per sync. Short enough that a genuine tick is never dropped: ticks are a second apart
         * and this is a fraction of that.
         */
        private const val FACE_DEDUPE_MS = 500L

        /**
         * How long a restarted clock loop waits before deciding on a rate.
         *
         * Long enough for the display to finish reporting the mode it is actually in, short enough
         * that the first refresh after a wrist raise is not visibly late.
         */
        private const val SETTLE_MS = 300L

        /**
         * How long a restarted loop waits for the producer to have a frame ready before ticking.
         *
         * About a second, which is what the wearer accepted as the cost of a second hand that is
         * regular from its first appearance.
         */
        private const val PRIME_TIMEOUT_MS = 1_200L
        private const val PRIME_POLL_MS = 50L

        /** How long the producer waits when the queue is already full, or the watch is dozing. */
        private const val PRODUCER_IDLE_MS = 1_000L

        /**
         * How many ticks in a row may find the queue empty before the rate drops.
         *
         * Three: enough to ride out a wake, where the queue is empty by construction, and short enough
         * that a watch which genuinely cannot keep up settles onto a slower rate within a few seconds.
         */
        private const val EMPTY_TICKS_BEFORE_SLOWING = 3

        /** Time allowed for a newly received zip to be written before it is read back. */
        private const val WATCH_FACE_STORE_MS = 400L

        /** How long to wait before checking that the new design really was applied. */
        private const val WATCH_FACE_RETRY_MS = 1_500L

        /**
         * The rate to ask at when no frame is ready, from how many ticks in a row have found none and
         * what the last frame cost.
         *
         * Pure arithmetic, and separate from the loop so the rule can be checked without a watch.
         *
         * An empty queue is normal for the first seconds after waking: everything was invalidated and
         * the producer needs a moment. Slowing down then is wrong twice over - the wearer sees the
         * second hand freeze at exactly the moment they raised their wrist. So the rate holds at one
         * second until several ticks in a row have found nothing, which is what a watch that cannot
         * keep up actually looks like. Only then does the measured frame cost decide how far to back
         * off, on the rule that a frame may use a third of its interval.
         */
        internal fun intervalWhenNothingPrepared(consecutiveEmptyTicks: Int, lastFrameMs: Long): Long = when {
            consecutiveEmptyTicks <= EMPTY_TICKS_BEFORE_SLOWING            -> SECOND_INTERVAL_MS
            lastFrameMs <= 0                                              -> SECOND_INTERVAL_MS
            lastFrameMs * FRAME_BUDGET_FRACTION <= SECOND_INTERVAL_MS     -> SECOND_INTERVAL_MS
            lastFrameMs * FRAME_BUDGET_FRACTION <= SLOW_INTERVAL_MS       -> SLOW_INTERVAL_MS
            else                                                          -> SLOWEST_INTERVAL_MS
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun requester(cls: Class<*>) =
        ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, cls))

    private val face by lazy { requester(CwfFaceComplication::class.java) }
    private val whole by lazy { requester(CwfImageComplication::class.java) }

    /**
     * The always-on slot, which publishes the same face without seconds.
     *
     * Asked for far less often than the awake one, and that is the point: without seconds its picture
     * only changes when the minute does. Pushing it every second would cost a delivery of tens of
     * kilobytes a second for a picture nobody could tell apart from the last one.
     */
    private val ambientFace by lazy { requester(CwfAmbientFaceComplication::class.java) }

    /**
     * The two always-on readouts.
     *
     * Asked for on a mode change and nowhere else. They carry their own values and refresh
     * themselves, but their **tap action** depends on the mode - it opens AAPS while awake and does
     * nothing while dozing, so the first tap on a sleeping watch wakes it. That action is decided
     * when the data is built, so without this it stays right for the mode before last: a whole
     * minute where tapping the readouts did nothing on an awake watch.
     */
    private val ambientReadouts by lazy {
        listOf(requester(CwfAmbientBgComplication::class.java), requester(CwfAmbientStatusComplication::class.java))
    }

    /** The minute the always-on slot was last asked for, so it is asked once per minute and no more. */
    private val lastAmbientMinute = AtomicLong(-1)

    /** When the face was last asked for, by any path, so a tick can skip a fresh redraw. */
    private val lastFaceRequest = AtomicLong(0)

    private fun requestFace() {
        lastFaceRequest.set(System.currentTimeMillis())
        face.requestUpdateAll()
        whole.requestUpdateAll()
    }

    /**
     * Asks the always-on slot for a new picture, at most once a minute.
     *
     * [force] for a change that makes the held picture wrong whatever the clock says - new data, a new
     * zip - and for the moment the watch dozes, when it is worth making sure the freshest one is in
     * the runtime's hands.
     */
    private fun requestAmbientFace(force: Boolean = false) {
        val minute = System.currentTimeMillis() / 60_000
        if (!force && lastAmbientMinute.get() == minute) return
        lastAmbientMinute.set(minute)
        ambientFace.requestUpdateAll()
    }

    /**
     * Marks the picture stale and asks for it again.
     *
     * The whole face is one image now, so there are no halves to keep in step: dropping the cached
     * layers and the prepared frames together is enough to guarantee that everything on screen comes
     * from the same data.
     */
    private fun refreshAll(reason: String) {
        aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: refresh all ($reason)")
        CwfFaceComplication.invalidate(everything = true)
        requestFace()
        requestAmbientFace(force = true)
        // The loop chooses its rate from whether seconds are shown, and that answer only changes once
        // a frame has been drawn with the new preference - by which time the loop is already asleep on
        // the rate it chose before. Turning seconds back on therefore drew the hand once and then left
        // it frozen for up to a minute, until the next ambient switch happened to restart the loop.
        // Anything worth a full refresh can change that answer, so the loop is restarted here too.
        startClockLoop()
    }

    /**
     * Asks for the picture again because the clock moved.
     *
     * Nothing is invalidated: only the two layers that carry the clock are redrawn, and the pipeline
     * knows that without being told. Skips itself when the face was drawn a moment ago for another
     * reason - see [FACE_DEDUPE_MS].
     */
    private fun refreshClock() {
        // The always-on picture is asked for on its own schedule - once a minute, which is as often
        // as it can change - and never skipped by the deduplication below, which exists for the awake
        // face's much faster rate.
        requestAmbientFace()
        if (System.currentTimeMillis() - lastFaceRequest.get() < FACE_DEDUPE_MS) return
        requestFace()
    }

    /** A refresh waiting to be issued: why it was asked for, and how much quiet it wants first. */
    private data class Pending(val reason: String, val quietMs: Long)

    /** Set by any trigger; the coalescing loop turns a burst of them into one refresh. */
    private val pending = AtomicReference<Pending?>(null)

    /** When the most recent trigger arrived, so the loop can wait for a quiet gap after it. */
    private val lastTrigger = AtomicLong(0)

    /**
     * Refreshes as soon as the watch enters or leaves ambient.
     *
     * Without this the picture lags a whole refresh behind the mode: the last render before dozing
     * is made while still active, so its second hand is drawn and then sits frozen through ambient,
     * and the first render after waking is made from a tick scheduled earlier that still reads doze,
     * so the hand is hidden while the watch is awake. Observed on device as the second hand appearing
     * in the wrong mode both ways round.
     *
     * `onDisplayChanged` also fires for changes that are not mode switches, so the state is compared
     * before asking for anything.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        private var wasAmbient: Boolean? = null
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val ambient = CwfFaceComplication.isAmbient(context)
            if (ambient != wasAmbient) {
                wasAmbient = ambient
                aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: ambient=$ambient secondsShown=${CwfFaceComplication.showsSeconds()}")
                // Refreshed here and now rather than through the coalescing loop. That loop sleeps a
                // second between passes, and the process is being frozen as the watch dozes, so a
                // refresh left pending often never got issued - the picture then kept the second hand
                // it was drawn with while awake, frozen, until the next minute tick. Coalescing exists
                // to tame bursts of data and preference events; a mode change is neither.
                // Not refreshAll: that invalidates the data too, and the data has not changed - only
                // the way it should be drawn has. Invalidating here is what made the first frame after
                // a wake rebuild every layer, throwing away the cache at the very moment it was needed.
                aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: mode changed, prepared frames dropped")
                CwfFaceComplication.modeChanged()
                requestFace()
                // Both ways round, and the waking direction matters as much as the other.
                //
                // Going to sleep: the runtime swaps to that slot by itself at the mode change, so
                // what matters is that what it holds is current - not how fast we can answer, which
                // was never fast enough.
                //
                // Waking: a slot's tap action is decided when its data is built, and that data
                // outlives the mode it was built in. The always-on picture is built while dozing,
                // when the tap action is deliberately left out so the first tap wakes the screen
                // instead of opening AAPS. Its slot covers the whole screen, so once awake it went on
                // swallowing taps - for a whole minute, until its own slow refresh came round. The
                // awake face never showed this because it is rebuilt every second and heals itself.
                requestAmbientFace(force = true)
                // Their tap action depends on the mode too - see [ambientReadouts]
                ambientReadouts.forEach { it.requestUpdateAll() }
                // The clock loop has already committed to an interval and is sleeping it out - up to
                // a minute in ambient - so it cannot notice the mode changed. Restart it so the new
                // rate takes effect now instead of whenever the old sleep happens to end.
                startClockLoop()
                // The producer sleeps a second at a time while dozing, so on waking it could be a
                // second before it starts refilling - a second taken straight out of the glance, and
                // out of the priming wait above. Put it back to work immediately.
                startProducer()
            }
        }
    }

    /**
     * Asks for a refresh once things have been quiet for [quietMs].
     *
     * When triggers of different kinds overlap the shortest wait wins: a preference change while
     * data is arriving means the user is looking at the watch, and should not wait out the longer
     * data gap.
     */
    private fun requestRefresh(reason: String, quietMs: Long) {
        pending.getAndUpdate { current ->
            if (current == null || quietMs < current.quietMs) Pending(reason, quietMs) else current
        }
        lastTrigger.set(System.currentTimeMillis())
    }

    /** Consecutive clock ticks that found no prepared frame. Reset as soon as one is ready. */
    private var emptyTicks = 0

    /**
     * Shows a newly sent zip, allowing for the fact that it is still being stored.
     *
     * Refreshes once the write has had time to land, then checks that the design really did change.
     * If it did not, the write was slower than expected and one more attempt is made rather than
     * leaving the wearer on the previous design until something else happens to trigger a rebuild.
     */
    private suspend fun refreshForNewWatchFace() {
        val before = CwfFaceComplication.currentStyleId()
        delay(WATCH_FACE_STORE_MS)
        refreshAll("watch face changed")
        delay(WATCH_FACE_RETRY_MS)
        if (CwfFaceComplication.currentStyleId() == before) {
            aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: watch face not applied yet, asking again")
            refreshAll("watch face changed, retry")
        }
    }

    private var clockJob: Job? = null
    private var producerJob: Job? = null

    /**
     * Keeps a few seconds of finished frames ready, so a request costs nothing to answer.
     *
     * Only while the watch is awake and actually showing seconds: in ambient the picture changes once
     * a minute, and a queue there would build frames nobody sees. A screen woken by a wrist gesture
     * stays on for about ten seconds, so the horizon is deliberately short - see
     * [CwfFrameQueue.HORIZON_FRAMES].
     *
     * Runs in the process, not in a service instance: a data source is bound only for as long as it
     * takes to answer one request, so nothing inside one can prepare anything for later. The process
     * stays alive because `DataLayerListenerServiceWear` is a foreground service.
     */
    private fun startProducer() {
        producerJob?.cancel()
        producerJob = scope.launch {
            while (true) {
                val wanted = !CwfFaceComplication.isAmbient(context) && CwfFaceComplication.showsSeconds()
                val built = wanted && CwfFaceComplication.prepareAhead(context, aapsLogger)
                // Nothing to build: wait out the second rather than spin. The queue empties as frames
                // are served, so there is work again within a second or so.
                if (!built) delay(PRODUCER_IDLE_MS)
            }
        }
    }

    /**
     * Refreshes the clock half on a grid, at a rate that depends on what is actually on screen.
     *
     * Per second only when seconds are really shown: the zip must ask for them, the user must not
     * have switched them off, and the watch must be awake. Any other case gains nothing from a faster
     * render and would only cost battery.
     *
     * Restarted rather than signalled when the mode changes, because the loop is inside a delay of up
     * to a minute and would otherwise keep the old rate until that sleep ended.
     */
    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = scope.launch {
            // A restart happens exactly when the display is least trustworthy: at a mode change, and
            // for a moment after a wrist raise it still reports doze. Choosing the interval then means
            // committing to the one minute ambient rate and sleeping through the whole glance - the
            // second hand frozen for as long as that sleep lasts. One short pause first, so the mode
            // is read once the display has settled.
            delay(SETTLE_MS)
            // Then wait, briefly, for the producer to have a frame ready.
            //
            // A wrist raise gives about five or six seconds of visible watch face. Showing a picture
            // at once and catching up afterwards spends three or four of those on an irregular second
            // hand, which is most of the glance. Waiting about a second and then ticking properly
            // spends one - and the wearer chose that trade: a second longer before the hand appears,
            // in exchange for it being right from the first frame.
            //
            // Bounded, because the wait must never become the fault it is fixing: if the producer
            // cannot manage a frame in that time, the tick goes ahead and draws its own.
            if (!CwfFaceComplication.isAmbient(context) && CwfFaceComplication.showsSeconds()) {
                val deadline = System.currentTimeMillis() + PRIME_TIMEOUT_MS
                while (CwfFaceComplication.preparedFrames() == 0 && System.currentTimeMillis() < deadline)
                    delay(PRIME_POLL_MS)
            }
            var announced = 0L
            while (true) {
                val interval =
                    if (!CwfFaceComplication.isAmbient(context) && CwfFaceComplication.showsSeconds()) secondsInterval()
                    else MINUTE_INTERVAL_MS
                if (interval != announced) {
                    announced = interval
                    aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: clock interval ${interval} ms (last frame ${CwfFaceComplication.lastFrameMs} ms)")
                }
                delay(interval - System.currentTimeMillis() % interval)
                refreshClock()
            }
        }
    }

    /**
     * The fastest rate this watch can actually sustain, from what the last frame cost.
     *
     * Zero means nothing has been drawn yet, and the optimistic guess is right there: the first
     * frames decide nothing and a slow watch corrects itself within a second or two.
     */
    private fun secondsInterval(): Long {
        // A request answered from the queue costs nothing, whatever the frame cost to build, so while
        // frames are ready the rate is one second - the only thing that has to keep up is the
        // producer, and it is already running flat out.
        if (CwfFaceComplication.preparedFrames() > 0) {
            emptyTicks = 0
            return SECOND_INTERVAL_MS
        }
        return intervalWhenNothingPrepared(++emptyTicks, CwfFaceComplication.lastFrameMs)
    }

    fun start() {
        startProducer()

        // A newly sent zip: refreshed at once, outside the coalescing queue.
        //
        // That queue waits for a second of quiet and restarts its wait on every further event - and a
        // zip arriving brings a burst of them, as its preferences are written and its data settles. So
        // the refresh kept being deferred. With seconds shown a tick every second hides it; with
        // seconds off the clock loop is at sixty seconds and nothing catches up, which is why AIMICO
        // could sit on the old design for minutes while the phone already showed the new one.
        //
        // A new watch face is not a burst to be tamed, it is one event - like a mode change, which is
        // handled outside that queue for the same reason. It is treated exactly as new data would be:
        // rebuild everything and show it now.
        //
        // The short wait is not a debounce but the storing of the zip: the handler writes it in its own
        // coroutine, and reading it before that finishes would draw the design being replaced. If the
        // style has not changed by then, one further attempt is made - a slow write should not cost
        // the wearer the whole minute.
        rxBus.toObservable(EventData.ActionSetCustomWatchface::class.java)
            .subscribe(
                { scope.launch { refreshForNewWatchFace() } },
                { aapsLogger.error(LTag.WEAR, "CwfComplicationUpdater: watch face stream failed", it) }
            )

        // A preference can change the layout itself, and the user is watching when they change one
        rxBus.toObservable(EventWearPreferenceChange::class.java)
            .subscribe({ requestRefresh("preference changed", COALESCE_MS) }, { aapsLogger.error(LTag.WEAR, "CwfComplicationUpdater: preference stream failed", it) })

        // New data from the phone, or a newly sent watch face. drop(1) skips the value the flow
        // replays on subscription, which is not a change and would refresh for nothing at startup.
        // drop(1) skips the value the flow replays on subscription, which is not a change and would
        // refresh for nothing at startup. The timestamp is dropped from the comparison because every
        // write stamps it with the current time, so two otherwise identical writes never compare
        // equal - it is only removed from the *comparison*, the render reloads the real data itself.
        scope.launch {
            complicationDataRepository.complicationData
                .map { it.copy(lastUpdateTimestamp = 0L) }
                .distinctUntilChanged()
                .drop(1)
                .collect { requestRefresh("data changed", DATA_COALESCE_MS) }
        }
        scope.launch {
            while (true) {
                delay(COALESCE_POLL_MS)
                val waiting = pending.get() ?: continue
                if (System.currentTimeMillis() - lastTrigger.get() >= waiting.quietMs)
                    pending.getAndSet(null)?.let { refreshAll(it.reason) }
            }
        }

        context.getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        startClockLoop()
        aapsLogger.debug(LTag.WEAR, "CwfComplicationUpdater: started")
    }
}
