package app.aaps.pump.omnipod.eros.history

import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Blocking wrapper around [ErosHistoryRecordDao].
 *
 * The calls stay blocking because `AapsOmnipodErosManager` is Java and cannot call a suspend
 * function. Room runs the suspend queries on its own executor, so only the insert needs an
 * explicit dispatcher to keep the database work off the calling thread.
 */
class ErosHistory(private val dao: ErosHistoryRecordDao) {

    fun getAllErosHistoryRecordsFromTimestamp(timeInMillis: Long): List<ErosHistoryRecordEntity> =
        runBlocking { dao.allSinceAsc(timeInMillis) }

    fun findErosHistoryRecordByPumpId(pumpId: Long): ErosHistoryRecordEntity? =
        runBlocking { dao.byId(pumpId) }

    fun create(historyRecord: ErosHistoryRecordEntity?): Long =
        runBlocking(Dispatchers.IO) { dao.insert(historyRecord!!) }
}
