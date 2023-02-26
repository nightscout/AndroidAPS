package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.transactions.TransactionGlucoseValue

/**
 * Inserts data from a CGM source into the database
 */
class CgmSourceTransaction constructor(
    private val glucoseValues: List<TransactionGlucoseValue>,
    private val calibrations: List<Calibration>,
    private val sensorInsertionTime: Long?
) : Transaction<CgmSourceTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        glucoseValues.forEach {
            val current = database.glucoseValueDao.findByTimestampAndSensor(it.timestamp, it.sourceSensor)
            val glucoseValue = GlucoseValue(
                timestamp = it.timestamp,
                raw = it.raw,
                value = it.value,
                noise = it.noise,
                trendArrow = it.trendArrow,
                sourceSensor = it.sourceSensor,
                isValid = it.isValid,
                utcOffset = it.utcOffset
            ).also { gv ->
                gv.interfaceIDs.nightscoutId = it.nightscoutId
            }
            // if nsId is not provided in new record, copy from current if exists
            if (glucoseValue.interfaceIDs.nightscoutId == null)
                current?.let { existing -> glucoseValue.interfaceIDs.nightscoutId = existing.interfaceIDs.nightscoutId }
            // preserve invalidated status (user may delete record in UI)
            current?.let { existing -> glucoseValue.isValid = existing.isValid }
            when {
                // new record, create new
                current == null                                                      -> {
                    database.glucoseValueDao.insertNewEntry(glucoseValue)
                    result.inserted.add(glucoseValue)
                }
                // different record, update
                !current.contentEqualsTo(glucoseValue)                               -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
                    result.updated.add(glucoseValue)
                }
                // update NS id if didn't exist and now provided
                current.interfaceIDs.nightscoutId == null && it.nightscoutId != null -> {
                    current.interfaceIDs.nightscoutId = it.nightscoutId
                    database.glucoseValueDao.updateExistingEntry(current)
                    result.updatedNsId.add(glucoseValue)
                }
            }
        }
        calibrations.forEach {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.FINGER_STICK_BG_VALUE, it.timestamp) == null) {
                val therapyEvent = TherapyEvent(
                    timestamp = it.timestamp,
                    type = TherapyEvent.Type.FINGER_STICK_BG_VALUE,
                    glucose = it.value,
                    glucoseUnit = it.glucoseUnit
                )
                database.therapyEventDao.insertNewEntry(therapyEvent)
                result.calibrationsInserted.add(therapyEvent)
            }
        }
        sensorInsertionTime?.let {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.SENSOR_CHANGE, it) == null) {
                val therapyEvent = TherapyEvent(
                    timestamp = it,
                    type = TherapyEvent.Type.SENSOR_CHANGE,
                    glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                )
                database.therapyEventDao.insertNewEntry(therapyEvent)
                result.sensorInsertionsInserted.add(therapyEvent)
            }
        }
        return result
    }

    data class Calibration(
        val timestamp: Long,
        val value: Double,
        val glucoseUnit: TherapyEvent.GlucoseUnit
    )

    class TransactionResult {

        val inserted = mutableListOf<GlucoseValue>()
        val updated = mutableListOf<GlucoseValue>()
        val updatedNsId = mutableListOf<GlucoseValue>()

        val calibrationsInserted = mutableListOf<TherapyEvent>()
        val sensorInsertionsInserted = mutableListOf<TherapyEvent>()

        fun all(): MutableList<GlucoseValue> =
            mutableListOf<GlucoseValue>().also { result ->
                result.addAll(inserted)
                result.addAll(updated)
            }
    }
}