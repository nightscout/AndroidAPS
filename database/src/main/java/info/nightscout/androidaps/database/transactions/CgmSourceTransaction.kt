package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent

/**
 * Inserts data from a CGM source into the database
 */
class CgmSourceTransaction(
    private val glucoseValues: List<TransactionGlucoseValue>,
    private val calibrations: List<Calibration>,
    private val sensorInsertionTime: Long?,
    private val syncer: Boolean = false // caller is not native source ie. NS
    // syncer is allowed create records
    // update synchronization ID
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
                sourceSensor = it.sourceSensor
            )
            glucoseValue.interfaceIDs.nightscoutId = it.nightscoutId
            // if nsId is not provided in new record, copy from current if exists
            if (glucoseValue.interfaceIDs.nightscoutId == null)
                current?.let { existing -> glucoseValue.interfaceIDs.nightscoutId = existing.interfaceIDs.nightscoutId }
            when {
                // new record, create new
                current == null                                                                -> {
                    database.glucoseValueDao.insertNewEntry(glucoseValue)
                    result.inserted.add(glucoseValue)
                }
                // different record, update
                !current.contentEqualsTo(glucoseValue) && !syncer                              -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
                    result.updated.add(glucoseValue)
                }
                // update NS id if didn't exist and now provided
                current.interfaceIDs.nightscoutId == null && it.nightscoutId != null && syncer -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
                    result.updated.add(glucoseValue)
                }
            }
        }
        calibrations.forEach {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.FINGER_STICK_BG_VALUE, it.timestamp) == null) {
                database.therapyEventDao.insertNewEntry(TherapyEvent(
                    timestamp = it.timestamp,
                    type = TherapyEvent.Type.FINGER_STICK_BG_VALUE,
                    amount = it.value
                ))
            }
        }
        sensorInsertionTime?.let {
            if (database.therapyEventDao.findByTimestamp(TherapyEvent.Type.SENSOR_INSERTED, it) == null) {
                database.therapyEventDao.insertNewEntry(TherapyEvent(
                    timestamp = it,
                    type = TherapyEvent.Type.SENSOR_INSERTED
                ))
            }
        }
        return result
    }

    data class TransactionGlucoseValue(
        val timestamp: Long,
        val value: Double,
        val raw: Double?,
        val noise: Double?,
        val trendArrow: GlucoseValue.TrendArrow,
        val nightscoutId: String? = null,
        val sourceSensor: GlucoseValue.SourceSensor
    )

    data class Calibration(
        val timestamp: Long,
        val value: Double
    )

    class TransactionResult {

        val inserted = mutableListOf<GlucoseValue>()
        val updated = mutableListOf<GlucoseValue>()

        fun all(): MutableList<GlucoseValue> =
            mutableListOf<GlucoseValue>().also { result ->
                result.addAll(inserted)
                result.addAll(updated)
            }
    }
}