package info.nightscout.androidaps.interfaces

import com.j256.ormlite.dao.CloseableIterator
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import org.json.JSONObject

interface DatabaseHelperInterface {

    fun resetDatabases()

    fun createOrUpdate(careportalEvent: CareportalEvent)
    fun createOrUpdate(extendedBolus: ExtendedBolus): Boolean
    fun createOrUpdate(profileSwitch: ProfileSwitch)
    fun createOrUpdate(record: DanaRHistoryRecord)
    fun createOrUpdate(record: OmnipodHistoryRecord)
    fun createOrUpdate(record: InsightBolusID)
    fun createOrUpdate(record: InsightPumpID)
    fun createOrUpdate(record: InsightHistoryOffset)
    fun create(record: DbRequest)
    fun getDanaRHistoryRecordsByType(type: Byte): List<DanaRHistoryRecord>
    fun getTDDs(): List<TDD>
    fun size(table: String): Long
    fun deleteAllDbRequests()
    fun deleteDbRequest(id: String): Int
    fun delete(tempBasal: TemporaryBasal)
    fun delete(extendedBolus: ExtendedBolus)
    fun deleteDbRequestbyMongoId(action: String, _id: String)
    fun getDbRequestInterator(): CloseableIterator<DbRequest>
    fun roundDateToSec(date: Long): Long
    fun createOrUpdateTDD(record: TDD)
    fun createOrUpdate(tempBasal: TemporaryBasal): Boolean
    fun findTempBasalByPumpId(id: Long): TemporaryBasal
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean): List<TemporaryBasal>
    fun getExtendedBolusDataFromTime(mills: Long, ascending: Boolean): List<ExtendedBolus>
    fun getCareportalEventFromTimestamp(timestamp: Long): CareportalEvent?
    fun getAllOmnipodHistoryRecordsFromTimestamp(timestamp: Long, ascending: Boolean): List<OmnipodHistoryRecord>
    fun findOmnipodHistoryRecordByPumpId(pumpId: Long): OmnipodHistoryRecord?
    fun getTDDsForLastXDays(days: Int): List<TDD>
    fun getProfileSwitchData(from: Long, ascending: Boolean): List<ProfileSwitch>
    fun getExtendedBolusByPumpId(pumpId: Long): ExtendedBolus?

    // old DB model
    fun deleteTempBasalById(_id: String)
    fun deleteExtendedBolusById(_id: String)
    fun deleteCareportalEventById(_id: String)
    fun deleteProfileSwitchById(_id: String)
    fun createTempBasalFromJsonIfNotExists(json: JSONObject)
    fun createExtendedBolusFromJsonIfNotExists(json: JSONObject)
    fun createCareportalEventFromJsonIfNotExists(json: JSONObject)
    fun createProfileSwitchFromJsonIfNotExists(activePluginProvider: ActivePluginProvider, nsUpload: NSUpload, trJson: JSONObject)

    fun getInsightBolusID(pumpSerial: String, bolusID: Int, timestamp: Long): InsightBolusID?
    fun getInsightHistoryOffset(pumpSerial: String): InsightHistoryOffset?
    fun getPumpStoppedEvent(pumpSerial: String, before: Long): InsightPumpID?

    companion object {

        const val DATABASE_INSIGHT_HISTORY_OFFSETS = "InsightHistoryOffsets"
        const val DATABASE_INSIGHT_BOLUS_IDS = "InsightBolusIDs"
        const val DATABASE_INSIGHT_PUMP_IDS = "InsightPumpIDs"
    }
}
