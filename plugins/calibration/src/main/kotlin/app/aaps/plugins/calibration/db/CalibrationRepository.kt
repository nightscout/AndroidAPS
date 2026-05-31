package app.aaps.plugins.calibration.db

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCalibrationChanged
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface CalibrationRepository {

    suspend fun insert(timestamp: Long, fingerstickMgdl: Double, sensorMgdlAtPairing: Double): Long
    suspend fun getAll(): List<CalibrationEntry>
    suspend fun getSince(from: Long): List<CalibrationEntry>
    fun observeAll(): Flow<List<CalibrationEntry>>
    suspend fun invalidate(id: Long)
    suspend fun deleteOlderThan(before: Long)
}

@Singleton
class CalibrationRepositoryImpl @Inject constructor(
    private val dao: CalibrationEntryDao,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus
) : CalibrationRepository {

    override suspend fun insert(timestamp: Long, fingerstickMgdl: Double, sensorMgdlAtPairing: Double): Long {
        val id = dao.insert(
            CalibrationEntry(
                timestamp = timestamp,
                fingerstickMgdl = fingerstickMgdl,
                sensorMgdlAtPairing = sensorMgdlAtPairing
            )
        )
        aapsLogger.debug(LTag.DATABASE) {
            "Inserted CalibrationEntry id=$id timestamp=$timestamp fingerstickMgdl=$fingerstickMgdl sensorMgdlAtPairing=$sensorMgdlAtPairing"
        }
        rxBus.send(EventCalibrationChanged())
        return id
    }

    override suspend fun getAll(): List<CalibrationEntry> = dao.getAll()
    override suspend fun getSince(from: Long): List<CalibrationEntry> = dao.getSince(from)
    override fun observeAll(): Flow<List<CalibrationEntry>> = dao.observeAll()

    override suspend fun invalidate(id: Long) {
        dao.invalidate(id)
        aapsLogger.debug(LTag.DATABASE) { "Invalidated CalibrationEntry id=$id" }
        rxBus.send(EventCalibrationChanged())
    }

    override suspend fun deleteOlderThan(before: Long) {
        dao.deleteOlderThan(before)
        aapsLogger.debug(LTag.DATABASE) { "Deleted CalibrationEntry rows older than $before" }
        rxBus.send(EventCalibrationChanged())
    }
}
