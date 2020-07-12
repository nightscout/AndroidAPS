package info.nightscout.androidaps.interfaces

import com.j256.ormlite.dao.CloseableIterator
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.DanaRHistoryRecord
import info.nightscout.androidaps.db.DbRequest
import info.nightscout.androidaps.db.TDD

interface DatabaseHelperInterface {

    fun getAllBgreadingsDataFromTime(mills: Long, ascending: Boolean): List<BgReading>
    fun createOrUpdate(careportalEvent: CareportalEvent)
    fun createOrUpdate(record: DanaRHistoryRecord)
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
    fun findTempBasalByPumpId(id: Long) : TemporaryBasal
    fun getTemporaryBasalsDataFromTime(mills: Long, ascending: Boolean) : List<TemporaryBasal>
    fun getCareportalEventFromTimestamp(timestamp: Long): CareportalEvent
    fun getTDDsForLastXDays(days: Int): List<TDD>
    fun getProfileSwitchData(from: Long, ascending: Boolean): List<ProfileSwitch>
}
