package info.nightscout.androidaps.interfaces

import com.j256.ormlite.dao.CloseableIterator
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import org.json.JSONObject

interface DatabaseHelperInterface {

    fun resetDatabases()

    fun createOrUpdate(extendedBolus: ExtendedBolus): Boolean
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
    fun delete(tempBasal: TemporaryBasal)
    fun delete(extendedBolus: ExtendedBolus)
    fun delete(profileSwitch: ProfileSwitch)
    fun deleteDbRequestbyMongoId(action: String, _id: String)
    fun getDbRequestIterator(): CloseableIterator<DbRequest>
    fun roundDateToSec(date: Long): Long
    fun createOrUpdateTDD(record: TDD)
    fun createOrUpdate(tempBasal: TemporaryBasal): Boolean
    fun findTempBasalByPumpId(id: Long): TemporaryBasal
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean): List<TemporaryBasal>
    fun getExtendedBolusDataFromTime(mills: Long, ascending: Boolean): List<ExtendedBolus>
    fun getProfileSwitchEventsFromTime(from: Long, to: Long, ascending: Boolean): List<ProfileSwitch>
    fun getProfileSwitchEventsFromTime(mills: Long, ascending: Boolean): List<ProfileSwitch>
    fun getAllOmnipodHistoryRecordsFromTimestamp(timestamp: Long, ascending: Boolean): List<OmnipodHistoryRecord>
    fun findOmnipodHistoryRecordByPumpId(pumpId: Long): OmnipodHistoryRecord?
    fun getTDDsForLastXDays(days: Int): List<TDD>
    fun getProfileSwitchData(from: Long, ascending: Boolean): List<ProfileSwitch>
    fun getExtendedBolusByPumpId(pumpId: Long): ExtendedBolus?
    fun getAllExtendedBoluses(): List<ExtendedBolus>
    fun getAllProfileSwitches(): List<ProfileSwitch>
    fun getAllTDDs(): List<TDD>
    fun getAllTemporaryBasals(): List<TemporaryBasal>
    fun getAllOHQueueItems(maxEntries: Long): List<OHQueueItem>
    fun resetProfileSwitch()

    // old DB model
    fun deleteTempBasalById(_id: String)
    fun deleteExtendedBolusById(_id: String)
    fun deleteProfileSwitchById(_id: String)
    fun createTempBasalFromJsonIfNotExists(json: JSONObject)
    fun createExtendedBolusFromJsonIfNotExists(json: JSONObject)
    fun createProfileSwitchFromJsonIfNotExists(activePluginProvider: ActivePluginProvider, nsUpload: NSUpload, trJson: JSONObject)

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
