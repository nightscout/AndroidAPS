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
    /**
     * True when this store already holds [gv] with the same data, so a change carrying it cannot alter
     * what a recalculation is built from.
     *
     * A database change tells us a row was written, not that anything the calculation reads is
     * different. The Nightscout id write-back is the common case: `updateExistingEntry` puts the row
     * into the same change list an insert uses, so a reading that only got its `nightscoutId` filled in
     * schedules a full stop, invalidate and restart of the calculation (issue #5101).
     *
     * Answers false unless the last load already saw a reading with this id and exactly this content.
     * Not held, held but different, store not loaded yet - all answer false. The answer is worded this
     * way round on purpose: false means "recalculate", which is both the safe direction and what a
     * mocked store returns by default.
     */
    fun holdsSameData(gv: GV): Boolean

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