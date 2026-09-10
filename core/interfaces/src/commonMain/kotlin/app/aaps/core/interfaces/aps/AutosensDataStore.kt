package app.aaps.core.interfaces.aps

import androidx.collection.LongSparseArray
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil

interface AutosensDataStore {

    /** Guards [bgReadings], [autosensDataTable] and [bucketedData]. Hold it across compound work. */
    val dataLock: AapsLock

    var bgReadings: List<GV>
    var autosensDataTable: LongSparseArray<AutosensData>
    var bucketedData: MutableList<InMemoryGlucoseValue>?
    var lastUsed5minCalculation: Boolean?

    /**
     * Return last valid (>39) InMemoryGlucoseValue from bucketed data or null if db is empty
     *
     * @return InMemoryGlucoseValue or null
     */
    fun lastBg(): InMemoryGlucoseValue?

    /**
     * Provide last bucketed InMemoryGlucoseValue or null if none exists within the last 9 minutes
     *
     * @return InMemoryGlucoseValue or null
     */
    fun actualBg(): InMemoryGlucoseValue?
    fun lastDataTime(dateUtil: DateUtil): String
    fun clone(): AutosensDataStore
    fun getBgReadingsDataTableCopy(): List<GV>
    fun getLastAutosensData(reason: String, aapsLogger: AAPSLogger, dateUtil: DateUtil): AutosensData?
    fun getAutosensDataAtTime(fromTime: Long): AutosensData?
    fun getBucketedDataTableCopy(): MutableList<InMemoryGlucoseValue>?
    fun createBucketedData(aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun slowAbsorptionPercentage(timeInMinutes: Int): Double
    fun newHistoryData(time: Long, aapsLogger: AAPSLogger, dateUtil: DateUtil)

    /**
     * Drops autosens entries older than [time], the counterpart of [newHistoryData] at the old end.
     *
     * Without it the table only ever grows, because nothing else removes an entry that has aged out of
     * the window the calculation works on: about 288 entries a day, each holding an autosens result,
     * kept for the whole life of the process (issue #5101).
     *
     * This is a memory bound, not a speed fix. Measured, the aged out head costs nothing worth naming:
     * the sensitivity plugins only compare its timestamps and move on. What costs time is how many
     * entries fall INSIDE the window, and that is bounded by the window itself once the bucket grid
     * stops moving.
     *
     * [time] must be at or older than the oldest data any reader can still ask for. The caller that
     * knows that is the one loading BG data, because the same window bounds the bucketed data.
     */
    fun pruneOlderThan(time: Long, aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun roundUpTime(time: Long): Long
    fun reset()
}