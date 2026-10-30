package app.aaps.core.objects.aps

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.utils.MidnightUtils

/**
 * The ISF for carbs: the ISF the COB calculation, the bolus wizard and the overview count carbs with.
 *
 * It is the average variable sensitivity (dynamic ISF) of the loop runs in the 24 hours before the time
 * asked for. A master and its clients all work it out with this code from the same stored loop results:
 * the master from its own, a client from the ones that came with the master's device statuses. So for the
 * same data every phone gets the same number (issue #5404). The finished value the master still puts in
 * the device status is not used for this: a client needs past times too, and a device status carries only
 * the newest value.
 *
 * The rules:
 * - Only results in (time - 24 h, time] count. A result after the time asked for is never used, so the
 *   value of a past time does not change when newer results come.
 * - One value for every 5 minutes, the last one in it. A burst of loop runs counts once. A client gets
 *   only the last run of a burst with the device status, so it sees the same as the master.
 * - When the newest result in the window has no value, the profile ISF is used. Dynamic ISF is off then,
 *   or the algorithm has none, or the master had no TDD and dosed with the profile ISF too. Results
 *   without a value are left out of the average.
 * - A loop run that stopped early on bad CGM data is never stored
 *   ([app.aaps.core.interfaces.aps.APSResult.stoppedEarly]). It has no value either, but it says nothing
 *   about dynamic ISF (#5413).
 *
 * @param rows the stored results, oldest first, as [PersistenceLayer.getLoopSensitivities] gives them
 */
class IsfForCarbs(rows: List<PersistenceLayer.LoopSensitivity>) {

    private val times = LongArray(rows.size) { rows[it].timestamp }

    // NaN means "no value". A plain array keeps the loop below free of boxing, it runs for every bucket.
    private val values = DoubleArray(rows.size) { i -> rows[i].variableSensMgdl?.takeIf { it.isFinite() && it > 0.0 } ?: Double.NaN }

    /** The average variable sensitivity at [timestamp] in mg/dL, or null when the profile ISF applies. */
    fun averageMgdl(timestamp: Long): Double? {
        val newest = lastIndexAtOrBefore(timestamp)
        val windowStart = timestamp - WINDOW_MS
        if (newest < 0 || times[newest] <= windowStart || values[newest].isNaN()) return null
        var sum = 0.0
        var count = 0
        // Backwards, so the first row seen in a slot is the last one in it
        var newerSlot = Long.MIN_VALUE
        var i = newest
        while (i >= 0 && times[i] > windowStart) {
            val slot = times[i].floorDiv(SLOT_MS)
            if (slot != newerSlot) {
                if (!values[i].isNaN()) {
                    sum += values[i]
                    count++
                }
                newerSlot = slot
            }
            i--
        }
        return sum / count
    }

    /**
     * The ISF for carbs at [timestamp] in mg/dL: [averageMgdl], else the profile ISF at that time.
     *
     * @param resultsUntil the newest time a loop result may have to count. The COB calculation gives the
     *   time of a bucket's own BG reading here, a little before [timestamp], see `PrepareGraphDataRunner`.
     */
    fun isfMgdl(profile: Profile, timestamp: Long, resultsUntil: Long = timestamp): Double =
        averageMgdl(resultsUntil) ?: profile.getIsfMgdlTimeFromMidnight(MidnightUtils.secondsFromMidnight(timestamp))

    private fun lastIndexAtOrBefore(timestamp: Long): Int {
        var low = 0
        var high = times.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (times[middle] <= timestamp) {
                found = middle
                low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    companion object {

        private val WINDOW_MS = T.hours(24).msecs()
        private val SLOT_MS = T.mins(5).msecs()

        /** Loads what the times from [from] to [to] need, so the results from 24 hours before [from] on. */
        suspend fun load(persistenceLayer: PersistenceLayer, from: Long, to: Long): IsfForCarbs =
            IsfForCarbs(persistenceLayer.getLoopSensitivities(from - WINDOW_MS, to))
    }
}
