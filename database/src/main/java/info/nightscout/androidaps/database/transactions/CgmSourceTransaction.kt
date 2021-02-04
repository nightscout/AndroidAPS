package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TherapyEvent

/**
 * Inserts data from a CGM source into the database
 */
class CgmSourceTransaction(
    private val glucoseValues: List<TransactionGlucoseValue>,
    private val calibrations: List<Calibration>,
    private val sensorInsertionTime: Long?
) : Transaction<List<GlucoseValue>>() {

    override fun run(): List<GlucoseValue> {
        val insertedGlucoseValues = mutableListOf<GlucoseValue>()
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
            when {
                current == null                        -> {
                    database.glucoseValueDao.insertNewEntry(glucoseValue)
                    insertedGlucoseValues.add(glucoseValue)
                }

                !current.contentEqualsTo(glucoseValue) -> {
                    glucoseValue.id = current.id
                    database.glucoseValueDao.updateExistingEntry(glucoseValue)
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
        return insertedGlucoseValues
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
}