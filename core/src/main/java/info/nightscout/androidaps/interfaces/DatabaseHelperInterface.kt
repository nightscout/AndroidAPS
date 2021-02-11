package info.nightscout.androidaps.interfaces

import com.j256.ormlite.dao.CloseableIterator
import info.nightscout.androidaps.db.*

interface DatabaseHelperInterface {

    fun createOrUpdate(careportalEvent: CareportalEvent)
    fun createOrUpdate(record: DanaRHistoryRecord)
    fun createOrUpdate(record: OmnipodHistoryRecord)
    fun create(record: DbRequest)
    fun getDanaRHistoryRecordsByType(type: Byte): List<DanaRHistoryRecord>
    fun getTDDs(): List<TDD>
    fun size(table: String): Long
    fun deleteAllDbRequests()
    fun deleteDbRequest(id: String): Int
    fun deleteDbRequestbyMongoId(action: String, _id: String)
    fun getDbRequestInterator(): CloseableIterator<DbRequest>
    fun roundDateToSec(date: Long): Long
    fun createOrUpdateTDD(record: TDD)
    fun createOrUpdate(tempBasal: TemporaryBasal)
    fun findTempBasalByPumpId(id: Long): TemporaryBasal
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean): List<TemporaryBasal>
    fun getCareportalEventFromTimestamp(timestamp: Long): CareportalEvent?
    fun getAllOmnipodHistoryRecordsFromTimestamp(timestamp: Long, ascending: Boolean): List<OmnipodHistoryRecord>
    fun findOmnipodHistoryRecordByPumpId(pumpId: Long): OmnipodHistoryRecord?
    fun getTDDsForLastXDays(days: Int): List<TDD>
    fun getProfileSwitchData(from: Long, ascending: Boolean): List<ProfileSwitch>
}
