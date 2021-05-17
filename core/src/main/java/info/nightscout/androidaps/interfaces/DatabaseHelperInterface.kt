package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.db.*

interface DatabaseHelperInterface {

    fun resetDatabases()

    fun createOrUpdate(record: OmnipodHistoryRecord)
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

    fun getOHQueueSize(): Long
    fun clearOpenHumansQueue()
    fun removeAllOHQueueItemsWithIdSmallerThan(id: Long)

}
