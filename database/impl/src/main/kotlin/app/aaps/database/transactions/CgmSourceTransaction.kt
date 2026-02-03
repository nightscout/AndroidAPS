package app.aaps.database.transactions

import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit

/**
 * Inserts data from a CGM source into the database
 */
class CgmSourceTransaction(
    private val glucoseValues: List<GlucoseValue>,
    private val calibrations: List<Calibration>,
    private val sensorInsertionTime: Long?
) : Transaction<CgmSourceTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        glucoseValues.forEach { glucoseValue ->

            /**
             * Teljane-only uniqueness rule:
             * - For Teljane, sgvId is the vendor unique id (per device scope).
             * - We must NOT allow duplicate sgvId rows.
             * - If (sourceSensor, sgvId) already exists -> SKIP (do NOT update).
             *
             * This relies on:
             * - UNIQUE index (sourceSensor, sgvId)
             * - GlucoseValueDao.insertTeljaneIfNew(...) using OnConflictStrategy.IGNORE
             *
             * Non-Teljane sources keep the original timestamp-based upsert logic unchanged.
             */
            val isTeljaneWithSgvId =
                glucoseValue.sourceSensor == GlucoseValue.SourceSensor.TELJANE &&
                    glucoseValue.sgvId != null

            if (isTeljaneWithSgvId) {
                val rowId = database.glucoseValueDao.insertTeljaneIfNew(glucoseValue)
                if (rowId != -1L) {
                    // Keep consistent with other inserts: set generated id and record as inserted
                    glucoseValue.id = rowId
                    result.inserted.add(glucoseValue)
                }
                // If rowId == -1, it was a duplicate (conflict) and was ignored.
                // Per requirement: skip, do NOT update.
                return@forEach
            }

            val current = database.glucoseValueDao.findByTimestampAndSensor(glucoseValue.timestamp, glucoseValue.sourceSensor)
            // if nsId is not provided in new record, copy from current if exists
            if (glucoseValue.interfaceIDs.nightscoutId == null)
                current?.let { existing -> glucoseValue.interfaceIDs.nightscoutId = existing.interfaceIDs.nightscoutId }
            // preserve invalidated status (user may delete record in UI)
            current?.let { existing -> glucoseValue.isValid = existing.isValid }
            when {
                // new record, create new
                current == null                                                                             -> {
                    database.glucoseValueDao.insertNewEntry(glucoseValue)
                    result.inserted.add(glucoseValue)
                }
                // different record, update
                !current.contentEqualsTo(glucoseValue)                                                      -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
                    result.updated.add(glucoseValue)
                }
                // update NS id if didn't exist and now provided
                current.interfaceIDs.nightscoutId == null && glucoseValue.interfaceIDs.nightscoutId != null -> {
                    current.interfaceIDs.nightscoutId = glucoseValue.interfaceIDs.nightscoutId
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
                val location = null
                val therapyEvent = TherapyEvent(
                    timestamp = it,
                    type = TherapyEvent.Type.SENSOR_CHANGE,
                    glucoseUnit = GlucoseUnit.MGDL,
                    location = location
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
        val glucoseUnit: GlucoseUnit
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