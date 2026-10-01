package app.aaps.wear.complications.cwf

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.abs

/**
 * Frames built before they are asked for, so the moment of truth costs nothing.
 *
 * The time is the one value that is known in advance: the second after this one is not a guess. So
 * while the watch is idle the pipeline can build the coming seconds, and when the system finally asks
 * for an update the answer already exists.
 *
 * That matters because of two measurements on a Galaxy Watch 4:
 *
 * - a frame takes 100 to 340 ms to produce, so a frame started when the request arrives is already
 *   late by the time it is shown - the picture always lags by that much;
 * - under load the system stops asking on time altogether. During an app install the second hand was
 *   seen moving at 5, 2, 3, 2, 20 and 20 seconds, and our slowest interval is 5 s, so those gaps were
 *   requests that never came. A prepared queue cannot conjure a request, but it can answer a late one
 *   with the frame that matches the moment it *arrived*, instead of one built for a second that has
 *   already passed.
 *
 * Frames carry the id of the data they were built from. When new data arrives the whole queue is
 * discarded rather than mixed: a picture must never show one reading's colours beside another's
 * value.
 */
/**
 * A frame that was prepared in advance, and the second it depicts.
 *
 * The two travel together because they can differ from the second that was asked for.
 */
data class PreparedFrame(val second: Long, val bytes: ByteArray) {

    // ByteArray compares by identity, which would make two equal frames unequal
    override fun equals(other: Any?): Boolean =
        this === other || (other is PreparedFrame && second == other.second && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * second.hashCode() + bytes.contentHashCode()
}

class CwfFrameQueue(private val aapsLogger: AAPSLogger) {

    companion object {

        /**
         * How many seconds are prepared ahead.
         *
         * Deliberately small. A screen woken by a wrist gesture stays on for a fixed ~10 s on Wear OS,
         * whatever the display timeout says, so a long horizon would spend most of its work on frames
         * nobody ever sees - and a frame costs the same energy whether it is shown or thrown away.
         * Five covers a data rebuild comfortably while wasting little.
         *
         * Kept here, at the top, because it is the one number worth trying at other values.
         */
        const val HORIZON_FRAMES = 5

        /**
         * How far from the wanted second a prepared frame may be and still be used.
         *
         * Half a second: beyond that the clock it depicts is visibly the wrong one, and building a
         * fresh frame is better than showing a stale second.
         */
        private const val TOLERANCE_MS = 500L
    }

    /** Prepared frames by the second they depict. Sorted, so the nearest one is cheap to find. */
    private val frames = ConcurrentSkipListMap<Long, ByteArray>()

    /** The data these frames were built from. A change empties the queue. */
    private var dataToken: Int = 0

    /** Whether these frames were built for the ambient look. */
    private var ambient: Boolean? = null

    /**
     * The frame for [second], or null if none was prepared close enough to it.
     *
     * Everything older than the wanted second is dropped on the way: a frame whose moment has passed
     * can never be used again, and holding it would only cost memory.
     *
     * The second the frame **depicts** comes back with it, and that is not always the one asked for -
     * the nearest prepared frame may sit up to [TOLERANCE_MS] either side. The caller has to know
     * which instant it is really showing, or it cannot stop the next frame from aiming earlier.
     */
    fun take(second: Long, token: Int, forAmbient: Boolean): PreparedFrame? {
        if (token != dataToken || ambient != forAmbient) return null
        frames.headMap(second - TOLERANCE_MS).clear()
        val nearest = frames.firstEntry() ?: return null
        if (abs(nearest.key - second) > TOLERANCE_MS) return null
        frames.remove(nearest.key)
        return PreparedFrame(nearest.key, nearest.value)
    }

    /** Adds a prepared frame. Ignored if the queue has moved on to other data since it was started. */
    fun offer(second: Long, token: Int, forAmbient: Boolean, bytes: ByteArray) {
        if (token != dataToken || ambient != forAmbient) return
        frames[second] = bytes
    }

    /**
     * The seconds, from [from], that are not prepared yet - what the producer should build next.
     *
     * Two rules, and both are needed:
     *
     * - **The series continues after whatever is already prepared**, not from the present instant. A
     *   frame takes 200 ms to over a second to produce, so a producer that always aimed at "now plus
     *   a bit" would keep re-aiming at seconds that had already gone by, and the queue would never
     *   get ahead - which is the whole point of it.
     * - **It never looks further than the horizon measured from [from]**, so a full queue means
     *   nothing to do. Without that cap the first rule runs away: once the queue was full the
     *   continuation started past the horizon and handed back five *new* future seconds on every
     *   call, for ever. The queue then drifted into the future until no prepared frame was near
     *   enough to be used, every request fell back to building a fresh frame, and the watch kept
     *   producing frames nobody would ever see. On a Galaxy Watch 4 that showed as the second and
     *   minute hands jumping **backwards**.
     */
    fun missing(from: Long): List<Long> {
        val lastPrepared = frames.lastEntry()?.key
        val after = lastPrepared?.let { maxOf(from, it + 1_000) } ?: from
        val horizonEnd = from + (HORIZON_FRAMES - 1) * 1_000
        return (0 until HORIZON_FRAMES)
            .map { after + it * 1_000 }
            .filter { it <= horizonEnd }
            .filterNot { frames.containsKey(it) }
    }

    /**
     * Starts a new generation: everything prepared so far is discarded.
     *
     * Called when the data changes or the watch enters or leaves ambient - both make every prepared
     * frame wrong, and a wrong frame shown for even one second is worse than a late one.
     */
    fun reset(token: Int, forAmbient: Boolean) {
        if (token == dataToken && ambient == forAmbient) return
        aapsLogger.debug(LTag.WEAR, "CwfFrameQueue: dropping ${frames.size} prepared frames (token=$token ambient=$forAmbient)")
        frames.clear()
        dataToken = token
        ambient = forAmbient
    }

    /** How many frames are ready. */
    val size: Int get() = frames.size

    fun clear() {
        frames.clear()
    }
}
