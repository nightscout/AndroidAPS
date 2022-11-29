package info.nightscout.interfaces.aps

import androidx.collection.LongSparseArray
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.utils.DateUtil

interface AutosensDataStore {

    val dataLock: Any

    var bgReadings: List<GlucoseValue>
    var autosensDataTable: LongSparseArray<AutosensData>
    var bucketedData: MutableList<InMemoryGlucoseValue>?
    var lastUsed5minCalculation: Boolean?

    fun lastBg(): GlucoseValue?
    fun actualBg(): GlucoseValue?
    fun lastDataTime(dateUtil: DateUtil): String
    fun clone(): AutosensDataStore
    fun getBgReadingsDataTableCopy(): List<GlucoseValue>
    fun getLastAutosensData(reason: String, aapsLogger: AAPSLogger, dateUtil: DateUtil): AutosensData?
    fun getAutosensDataAtTime(fromTime: Long): AutosensData?
    fun getBucketedDataTableCopy(): MutableList<InMemoryGlucoseValue>?
    fun createBucketedData(aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun slowAbsorptionPercentage(timeInMinutes: Int): Double
    fun newHistoryData(time: Long, aapsLogger: AAPSLogger, dateUtil: DateUtil)
    fun roundUpTime(time: Long): Long
    fun reset()
}