package info.nightscout.core.iob.iobCobCalculator

import androidx.collection.LongSparseArray
import androidx.collection.size
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.aps.AutosensData
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlin.math.abs
import kotlin.math.roundToLong

@OpenForTesting
class AutosensDataStoreObject : AutosensDataStore {

    override val dataLock = Any()
    override var lastUsed5minCalculation: Boolean? = null // true if used 5min bucketed data

    // we need to make sure that bucketed_data will always have the same timestamp for correct use of cached values
    // once referenceTime != null all bucketed data should be (x * 5min) from referenceTime
    var referenceTime: Long = -1

    override var bgReadings: List<GlucoseValue> = listOf() // newest at index 0
        @Synchronized set
        @Synchronized get

    override var autosensDataTable = LongSparseArray<AutosensData>() // oldest at index 0
        @Synchronized set
        @Synchronized get

    override var bucketedData: MutableList<InMemoryGlucoseValue>? = null
        @Synchronized set
        @Synchronized get

    override fun clone(): AutosensDataStore =
        AutosensDataStoreObject().also {
            synchronized(dataLock) {
                it.bgReadings = this.bgReadings.toMutableList()
                it.autosensDataTable = LongSparseArray<AutosensData>(this.autosensDataTable.size).apply { putAll(this@AutosensDataStoreObject.autosensDataTable) }
                it.bucketedData = this.bucketedData?.toMutableList()
            }
        }

    override fun getBucketedDataTableCopy(): MutableList<InMemoryGlucoseValue>? = synchronized(dataLock) { bucketedData?.toMutableList() }
    override fun getBgReadingsDataTableCopy(): List<GlucoseValue> = synchronized(dataLock) { bgReadings.toMutableList() }

    override fun reset() {
        synchronized(autosensDataTable) { autosensDataTable = LongSparseArray() }
    }

    override fun newHistoryData(time: Long, aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        synchronized(autosensDataTable) {
            for (index in autosensDataTable.size() - 1 downTo 0) {
                if (autosensDataTable.keyAt(index) > time) {
                    aapsLogger.debug(LTag.AUTOSENS) { "Removing from autosensDataTable: ${dateUtil.dateAndTimeAndSecondsString(autosensDataTable.keyAt(index))}" }
                    autosensDataTable.removeAt(index)
                } else {
                    break
                }
            }
        }
    }

    // roundup to whole minute
    override fun roundUpTime(time: Long): Long {
        return if (time % 60000 == 0L) time else (time / 60000 + 1) * 60000
    }

    /**
     * Return last valid (>39) GlucoseValue from database or null if db is empty
     *
     * @return GlucoseValue or null
     */
    override fun lastBg(): GlucoseValue? =
        synchronized(dataLock) {
            if (bgReadings.isNotEmpty()) bgReadings[0]
            else null
        }

    /**
     * Provide last GlucoseValue or null if none exists within the last 9 minutes
     *
     * @return GlucoseValue or null
     */
    override fun actualBg(): GlucoseValue? {
        val lastBg = lastBg() ?: return null
        return if (lastBg.timestamp > System.currentTimeMillis() - T.mins(9).msecs()) lastBg else null
    }

    override fun lastDataTime(dateUtil: DateUtil): String =
        synchronized(dataLock) {
            if (autosensDataTable.size() > 0) dateUtil.dateAndTimeAndSecondsString(autosensDataTable.valueAt(autosensDataTable.size() - 1).time)
            else "autosensDataTable empty"
        }

    fun findPreviousTimeFromBucketedData(time: Long): Long? {
        val bData = bucketedData ?: return null
        for (index in bData.indices) {
            if (bData[index].timestamp <= time) return bData[index].timestamp
        }
        return null
    }

    override fun getAutosensDataAtTime(fromTime: Long): AutosensData? {
        synchronized(dataLock) {
            val now = System.currentTimeMillis()
            if (fromTime > now) return null
            val previous = findPreviousTimeFromBucketedData(fromTime) ?: return null
            return autosensDataTable[roundUpTime(previous)]
        }
    }

    override fun getLastAutosensData(reason: String, aapsLogger: AAPSLogger, dateUtil: DateUtil): AutosensData? {
        synchronized(dataLock) {
            if (autosensDataTable.size() < 1) {
                aapsLogger.debug(LTag.AUTOSENS, "AUTOSENSDATA null: autosensDataTable empty ($reason)")
                return null
            }
            val data: AutosensData? = try {
                autosensDataTable.valueAt(autosensDataTable.size() - 1)
            } catch (e: Exception) {
                // data can be processed on the background
                // in this rare case better return null and do not block UI
                // APS plugin should use getLastAutosensDataSynchronized where the blocking is not an issue
                aapsLogger.error("AUTOSENSDATA null: Exception caught ($reason)")
                return null
            }
            if (data == null) {
                aapsLogger.error("AUTOSENSDATA null: data==null")
                return null
            }
            return if (data.time < System.currentTimeMillis() - 11 * 60 * 1000) {
                aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA null: data is old ($reason) size()=${autosensDataTable.size()} lastData=${dateUtil.dateAndTimeAndSecondsString(data.time)}" }
                null
            } else {
                aapsLogger.debug(LTag.AUTOSENS) { "AUTOSENSDATA ($reason) $data" }
                data
            }
        }
    }

    private fun adjustToReferenceTime(someTime: Long): Long {
        if (referenceTime == -1L) {
            referenceTime = someTime
            return someTime
        }
        var diff = abs(someTime - referenceTime)
        diff %= T.mins(5).msecs()
        if (diff > T.mins(2).plus(T.secs(30)).msecs()) diff -= T.mins(5).msecs()
        return someTime + diff
    }

    fun isAbout5minData(aapsLogger: AAPSLogger): Boolean {
        synchronized(dataLock) {
            if (bgReadings.size < 3) return true

            var totalDiff: Long = 0
            for (i in 1 until bgReadings.size) {
                val bgTime = bgReadings[i].timestamp
                val lastBgTime = bgReadings[i - 1].timestamp
                var diff = lastBgTime - bgTime
                diff %= T.mins(5).msecs()
                if (diff > T.mins(2).plus(T.secs(30)).msecs()) diff -= T.mins(5).msecs()
                totalDiff += diff
                diff = abs(diff)
                if (diff > T.secs(30).msecs()) {
                    aapsLogger.debug(LTag.AUTOSENS, "Interval detection: values: ${bgReadings.size} diff: ${diff / 1000}[s] is5minData: false")
                    return false
                }
            }
            val averageDiff = totalDiff / bgReadings.size / 1000
            val is5minData = averageDiff < 1
            aapsLogger.debug(LTag.AUTOSENS, "Interval detection: values: ${bgReadings.size} averageDiff: $averageDiff[s] is5minData: $is5minData")
            return is5minData
        }
    }

    override fun createBucketedData(aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        val fiveMinData = isAbout5minData(aapsLogger)
        if (lastUsed5minCalculation != null && lastUsed5minCalculation != fiveMinData) {
            // changing mode => clear cache
            aapsLogger.debug("Invalidating cached data because of changed mode.")
            reset()
        }
        lastUsed5minCalculation = fiveMinData
        if (fiveMinData) createBucketedData5min(aapsLogger, dateUtil) else createBucketedDataRecalculated(aapsLogger, dateUtil)
    }

    fun findNewer(time: Long): GlucoseValue? {
        var lastFound = bgReadings[0]
        if (lastFound.timestamp < time) return null
        for (i in 1 until bgReadings.size) {
            if (bgReadings[i].timestamp == time) return bgReadings[i]
            if (bgReadings[i].timestamp > time) continue
            lastFound = bgReadings[i - 1]
            if (bgReadings[i].timestamp < time) break
        }
        return lastFound
    }

    fun findOlder(time: Long): GlucoseValue? {
        var lastFound = bgReadings[bgReadings.size - 1]
        if (lastFound.timestamp > time) return null
        for (i in bgReadings.size - 2 downTo 0) {
            if (bgReadings[i].timestamp == time) return bgReadings[i]
            if (bgReadings[i].timestamp < time) continue
            lastFound = bgReadings[i + 1]
            if (bgReadings[i].timestamp > time) break
        }
        return lastFound
    }

    private fun createBucketedDataRecalculated(aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        if (bgReadings.size < 3) {
            bucketedData = null
            return
        }
        val newBucketedData = ArrayList<InMemoryGlucoseValue>()
        var currentTime = bgReadings[0].timestamp - bgReadings[0].timestamp % T.mins(5).msecs()
        val adjustedTime = adjustToReferenceTime(currentTime)
        // after adjusting time may be newer. In this case use T-5min
        currentTime = if (adjustedTime > currentTime) adjustedTime - T.mins(5).msecs() else adjustedTime
        aapsLogger.debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(currentTime))
        //log.debug("First reading: " + new Date(currentTime).toLocaleString());
        while (true) {
            // test if current value is older than current time
            val newer = findNewer(currentTime)
            val older = findOlder(currentTime)
            if (newer == null || older == null) break
            if (older.timestamp == newer.timestamp) { // direct hit
                newBucketedData.add(InMemoryGlucoseValue(newer))
            } else {
                val bgDelta = newer.value - older.value
                val timeDiffToNew = newer.timestamp - currentTime
                val currentBg = newer.value - timeDiffToNew.toDouble() / (newer.timestamp - older.timestamp) * bgDelta
                val newBgReading = InMemoryGlucoseValue(currentTime, currentBg.roundToLong().toDouble(), true)
                newBucketedData.add(newBgReading)
                //log.debug("BG: " + newBgReading.value + " (" + new Date(newBgReading.date).toLocaleString() + ") Prev: " + older.value + " (" + new Date(older.date).toLocaleString() + ") Newer: " + newer.value + " (" + new Date(newer.date).toLocaleString() + ")");
            }
            currentTime -= T.mins(5).msecs()
        }
        bucketedData = newBucketedData
    }

    private fun createBucketedData5min(aapsLogger: AAPSLogger, dateUtil: DateUtil) {
        if (bgReadings.size < 3) {
            bucketedData = null
            return
        }
        val bData: MutableList<InMemoryGlucoseValue> = ArrayList()
        bData.add(InMemoryGlucoseValue(bgReadings[0]))
        aapsLogger.debug(LTag.AUTOSENS) { "Adding. bgTime: ${dateUtil.toISOString(bgReadings[0].timestamp)} lastBgTime: none-first-value ${bgReadings[0]}" }
        var j = 0
        for (i in 1 until bgReadings.size) {
            val bgTime = bgReadings[i].timestamp
            var lastBgTime = bgReadings[i - 1].timestamp
            //log.error("Processing " + i + ": " + new Date(bgTime).toString() + " " + bgReadings.get(i).value + "   Previous: " + new Date(lastBgTime).toString() + " " + bgReadings.get(i - 1).value);
            var elapsedMinutes = (bgTime - lastBgTime) / (60 * 1000)
            when {
                abs(elapsedMinutes) > 8 -> {
                    // interpolate missing data points
                    var lastBg = bgReadings[i - 1].value
                    elapsedMinutes = abs(elapsedMinutes)
                    //console.error(elapsed_minutes);
                    var nextBgTime: Long
                    while (elapsedMinutes > 5) {
                        nextBgTime = lastBgTime - 5 * 60 * 1000
                        j++
                        val gapDelta = bgReadings[i].value - lastBg
                        //console.error(gapDelta, lastBg, elapsed_minutes);
                        val nextBg = lastBg + 5.0 / elapsedMinutes * gapDelta
                        val newBgReading = InMemoryGlucoseValue(nextBgTime, nextBg.roundToLong().toDouble(), true)
                        //console.error("Interpolated", bData[j]);
                        bData.add(newBgReading)
                        aapsLogger.debug(LTag.AUTOSENS) { "Adding. bgTime: ${dateUtil.toISOString(bgTime)} lastBgTime: ${dateUtil.toISOString(lastBgTime)} $newBgReading" }
                        elapsedMinutes -= 5
                        lastBg = nextBg
                        lastBgTime = nextBgTime
                    }
                    j++
                    val newBgReading = InMemoryGlucoseValue(bgTime, bgReadings[i].value)
                    bData.add(newBgReading)
                    aapsLogger.debug(LTag.AUTOSENS) { "Adding. bgTime: ${dateUtil.toISOString(bgTime)} lastBgTime: ${dateUtil.toISOString(lastBgTime)} $newBgReading" }
                }

                abs(elapsedMinutes) > 2 -> {
                    j++
                    val newBgReading = InMemoryGlucoseValue(bgTime, bgReadings[i].value)
                    bData.add(newBgReading)
                    aapsLogger.debug(LTag.AUTOSENS) { "Adding. bgTime: ${dateUtil.toISOString(bgTime)} lastBgTime: ${dateUtil.toISOString(lastBgTime)} $newBgReading" }
                }

                else                    -> {
                    bData[j].value = (bData[j].value + bgReadings[i].value) / 2
                    //log.error("***** Average");
                }
            }
        }

        // Normalize bucketed data
        val oldest = bData[bData.size - 1]
        oldest.timestamp = adjustToReferenceTime(oldest.timestamp)
        aapsLogger.debug("Adjusted time " + dateUtil.dateAndTimeAndSecondsString(oldest.timestamp))
        for (i in bData.size - 2 downTo 0) {
            val current = bData[i]
            val previous = bData[i + 1]
            val mSecDiff = current.timestamp - previous.timestamp
            val adjusted = (mSecDiff - T.mins(5).msecs()) / 1000
            aapsLogger.debug(LTag.AUTOSENS) { "Adjusting bucketed data time. Current: ${dateUtil.dateAndTimeAndSecondsString(current.timestamp)} to: ${dateUtil.dateAndTimeAndSecondsString(previous.timestamp + T.mins(5).msecs())} by $adjusted sec" }
            if (abs(adjusted) > 90) {
                // too big adjustment, fallback to non 5 min data
                aapsLogger.debug(LTag.AUTOSENS, "Fallback to non 5 min data")
                createBucketedDataRecalculated(aapsLogger, dateUtil)
                return
            }
            current.timestamp = previous.timestamp + T.mins(5).msecs()
        }
        aapsLogger.debug(LTag.AUTOSENS, "Bucketed data created. Size: " + bData.size)
        bucketedData = bData
    }

    override fun slowAbsorptionPercentage(timeInMinutes: Int): Double {
        var sum = 0.0
        var count = 0
        val valuesToProcess = timeInMinutes / 5
        synchronized(dataLock) {
            var i = autosensDataTable.size() - 1
            while (i >= 0 && count < valuesToProcess) {
                if (autosensDataTable.valueAt(i).failOverToMinAbsorptionRate) sum++
                count++
                i--
            }
        }
        return if (count != 0) sum / count else 0.0
    }
}
