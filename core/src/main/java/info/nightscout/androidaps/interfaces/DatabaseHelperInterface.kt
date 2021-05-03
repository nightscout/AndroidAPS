package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.db.*

interface DatabaseHelperInterface {

    fun resetDatabases()

    fun createOrUpdate(record: OmnipodHistoryRecord)
    fun createOrUpdate(record: InsightBolusID)
    fun createOrUpdate(record: InsightPumpID)
    fun createOrUpdate(record: InsightHistoryOffset)
    fun createOrUpdate(record: OHQueueItem)
    fun delete(extendedBolus: ExtendedBolus)
    fun createOrUpdate(tempBasal: TemporaryBasal): Boolean
    @Deprecated("Use new DB")
    fun findTempBasalByPumpId(id: Long): TemporaryBasal?
    @Deprecated("Use new DB")
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean): List<TemporaryBasal>
    fun getAllOmnipodHistoryRecordsFromTimestamp(timestamp: Long, ascending: Boolean): List<OmnipodHistoryRecord>
    fun findOmnipodHistoryRecordByPumpId(pumpId: Long): OmnipodHistoryRecord?
    @Deprecated("Use new DB")
    fun getExtendedBolusByPumpId(pumpId: Long): ExtendedBolus?
    fun getAllOHQueueItems(maxEntries: Long): List<OHQueueItem>

    // old DB model
    fun getInsightBolusID(pumpSerial: String, bolusID: Int, timestamp: Long): InsightBolusID?
    fun getInsightHistoryOffset(pumpSerial: String): InsightHistoryOffset?
    fun getPumpStoppedEvent(pumpSerial: String, before: Long): InsightPumpID?

    fun getOHQueueSize(): Long
    fun clearOpenHumansQueue()
    fun removeAllOHQueueItemsWithIdSmallerThan(id: Long)

    companion object {

        const val DATABASE_INSIGHT_HISTORY_OFFSETS = "InsightHistoryOffsets"
        const val DATABASE_INSIGHT_BOLUS_IDS = "InsightBolusIDs"
        const val DATABASE_INSIGHT_PUMP_IDS = "InsightPumpIDs"
    }
}
