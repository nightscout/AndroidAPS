package info.nightscout.androidaps.interfaces

import com.j256.ormlite.dao.CloseableIterator
import info.nightscout.androidaps.db.*
import org.json.JSONObject

interface DatabaseHelperInterface {

    fun resetDatabases()

    fun createOrUpdate(profileSwitch: ProfileSwitch)
    fun createOrUpdate(record: DanaRHistoryRecord)
    fun createOrUpdate(record: OmnipodHistoryRecord)
    fun createOrUpdate(record: InsightBolusID)
    fun createOrUpdate(record: InsightPumpID)
    fun createOrUpdate(record: InsightHistoryOffset)
    fun createOrUpdate(record: OHQueueItem)
    fun create(record: DbRequest)
    fun getDanaRHistoryRecordsByType(type: Byte): List<DanaRHistoryRecord>
    fun getTDDs(): List<TDD>
    fun size(table: String): Long
    fun deleteAllDbRequests()
    fun deleteDbRequest(id: String): Int
    fun delete(extendedBolus: ExtendedBolus)
    fun delete(profileSwitch: ProfileSwitch)
    fun deleteDbRequestbyMongoId(action: String, _id: String)
    fun getDbRequestIterator(): CloseableIterator<DbRequest>
    fun roundDateToSec(date: Long): Long
    fun createOrUpdateTDD(record: TDD)
    fun createOrUpdate(tempBasal: TemporaryBasal): Boolean
    @Deprecated("Use new DB")
    fun findTempBasalByPumpId(id: Long): TemporaryBasal?
    @Deprecated("Use new DB")
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean): List<TemporaryBasal>
    fun getProfileSwitchEventsFromTime(from: Long, to: Long, ascending: Boolean): List<ProfileSwitch>
    fun getProfileSwitchEventsFromTime(mills: Long, ascending: Boolean): List<ProfileSwitch>
    fun getAllOmnipodHistoryRecordsFromTimestamp(timestamp: Long, ascending: Boolean): List<OmnipodHistoryRecord>
    fun findOmnipodHistoryRecordByPumpId(pumpId: Long): OmnipodHistoryRecord?
    fun getTDDsForLastXDays(days: Int): List<TDD>
    fun getProfileSwitchData(from: Long, ascending: Boolean): List<ProfileSwitch>
    @Deprecated("Use new DB")
    fun getExtendedBolusByPumpId(pumpId: Long): ExtendedBolus?
    fun getAllProfileSwitches(): List<ProfileSwitch>
    fun getAllTDDs(): List<TDD>
    fun getAllOHQueueItems(maxEntries: Long): List<OHQueueItem>
    fun resetProfileSwitch()

    // old DB model
    fun deleteProfileSwitchById(_id: String)
    fun createProfileSwitchFromJsonIfNotExists(trJson: JSONObject)

    fun getInsightBolusID(pumpSerial: String, bolusID: Int, timestamp: Long): InsightBolusID?
    fun getInsightHistoryOffset(pumpSerial: String): InsightHistoryOffset?
    fun getPumpStoppedEvent(pumpSerial: String, before: Long): InsightPumpID?

    fun getOHQueueSize(): Long
    fun clearOpenHumansQueue()
    fun getCountOfAllRows(): Long
    fun removeAllOHQueueItemsWithIdSmallerThan(id: Long)

    companion object {

        const val DATABASE_INSIGHT_HISTORY_OFFSETS = "InsightHistoryOffsets"
        const val DATABASE_INSIGHT_BOLUS_IDS = "InsightBolusIDs"
        const val DATABASE_INSIGHT_PUMP_IDS = "InsightPumpIDs"
    }
}
