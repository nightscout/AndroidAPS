package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.GlucoseValueDao
import app.aaps.database.daos.TherapyEventDao
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CgmSourceTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var glucoseValueDao: GlucoseValueDao
    private lateinit var therapyEventDao: TherapyEventDao

    @BeforeEach
    fun setup() {
        glucoseValueDao = mock()
        therapyEventDao = mock()
        database = mock()
        whenever(database.glucoseValueDao).thenReturn(glucoseValueDao)
        whenever(database.therapyEventDao).thenReturn(therapyEventDao)
    }

    @Test
    fun `inserts new glucose value when not found`() {
        val gv = createGlucoseValue(timestamp = 1000L, value = 120.0)

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(null)

        val transaction = CgmSourceTransaction(listOf(gv), emptyList(), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updated).isEmpty()

        verify(glucoseValueDao).insertNewEntry(gv)
    }

    @Test
    fun `updates glucose value when content changes`() {
        val gv = createGlucoseValue(timestamp = 1000L, value = 130.0)
        val existing = createGlucoseValue(timestamp = 1000L, value = 120.0)

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(existing)

        val transaction = CgmSourceTransaction(listOf(gv), emptyList(), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.inserted).isEmpty()

        verify(glucoseValueDao).updateExistingEntry(gv)
    }

    @Test
    fun `updates nsId when provided but not present in existing`() {
        val gv = createGlucoseValue(timestamp = 1000L, value = 120.0, nsId = "ns-123")
        val existing = createGlucoseValue(timestamp = 1000L, value = 120.0, nsId = null)

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(existing)

        val transaction = CgmSourceTransaction(listOf(gv), emptyList(), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(glucoseValueDao).updateExistingEntry(existing)
    }

    @Test
    fun `preserves nsId from existing when not provided in new`() {
        val gv = createGlucoseValue(timestamp = 1000L, value = 120.0, nsId = null)
        val existing = createGlucoseValue(timestamp = 1000L, value = 120.0, nsId = "ns-123")

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(existing)

        val transaction = CgmSourceTransaction(listOf(gv), emptyList(), null)
        transaction.database = database
        transaction.run()

        assertThat(gv.interfaceIDs.nightscoutId).isEqualTo("ns-123")
    }

    @Test
    fun `preserves invalid status from existing`() {
        val gv = createGlucoseValue(timestamp = 1000L, value = 120.0, isValid = true)
        val existing = createGlucoseValue(timestamp = 1000L, value = 120.0, isValid = false)

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(existing)

        val transaction = CgmSourceTransaction(listOf(gv), emptyList(), null)
        transaction.database = database
        transaction.run()

        assertThat(gv.isValid).isFalse()
    }

    @Test
    fun `inserts calibration as therapy event`() {
        val calibration = CgmSourceTransaction.Calibration(
            timestamp = 2000L,
            value = 110.0,
            glucoseUnit = GlucoseUnit.MGDL
        )

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.FINGER_STICK_BG_VALUE, 2000L)).thenReturn(null)

        val transaction = CgmSourceTransaction(emptyList(), listOf(calibration), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.calibrationsInserted).hasSize(1)
        assertThat(result.calibrationsInserted[0].glucose).isEqualTo(110.0)

        verify(therapyEventDao).insertNewEntry(result.calibrationsInserted[0])
    }

    @Test
    fun `does not insert calibration if already exists`() {
        val calibration = CgmSourceTransaction.Calibration(
            timestamp = 2000L,
            value = 110.0,
            glucoseUnit = GlucoseUnit.MGDL
        )
        val existingTherapyEvent = createTherapyEvent(timestamp = 2000L, type = TherapyEvent.Type.FINGER_STICK_BG_VALUE)

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.FINGER_STICK_BG_VALUE, 2000L)).thenReturn(existingTherapyEvent)

        val transaction = CgmSourceTransaction(emptyList(), listOf(calibration), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.calibrationsInserted).isEmpty()
    }

    @Test
    fun `inserts sensor insertion as therapy event`() {
        val sensorInsertionTime = 3000L

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.SENSOR_CHANGE, 3000L)).thenReturn(null)

        val transaction = CgmSourceTransaction(emptyList(), emptyList(), sensorInsertionTime)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.sensorInsertionsInserted).hasSize(1)
        assertThat(result.sensorInsertionsInserted[0].timestamp).isEqualTo(3000L)

        verify(therapyEventDao).insertNewEntry(result.sensorInsertionsInserted[0])
    }

    @Test
    fun `does not insert sensor insertion if already exists`() {
        val sensorInsertionTime = 3000L
        val existingTherapyEvent = createTherapyEvent(timestamp = 3000L, type = TherapyEvent.Type.SENSOR_CHANGE)

        whenever(therapyEventDao.findByTimestamp(TherapyEvent.Type.SENSOR_CHANGE, 3000L)).thenReturn(existingTherapyEvent)

        val transaction = CgmSourceTransaction(emptyList(), emptyList(), sensorInsertionTime)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.sensorInsertionsInserted).isEmpty()
    }

    @Test
    fun `handles multiple glucose values`() {
        val gv1 = createGlucoseValue(timestamp = 1000L, value = 120.0)
        val gv2 = createGlucoseValue(timestamp = 2000L, value = 125.0)

        whenever(glucoseValueDao.findByTimestampAndSensor(1000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(null)
        whenever(glucoseValueDao.findByTimestampAndSensor(2000L, GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)).thenReturn(null)

        val transaction = CgmSourceTransaction(listOf(gv1, gv2), emptyList(), null)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
        assertThat(result.all()).hasSize(2)
    }

    private fun createGlucoseValue(
        timestamp: Long,
        value: Double,
        nsId: String? = null,
        isValid: Boolean = true
    ): GlucoseValue = GlucoseValue(
        timestamp = timestamp,
        value = value,
        raw = value,
        noise = null,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    )

    private fun createTherapyEvent(
        timestamp: Long,
        type: TherapyEvent.Type
    ): TherapyEvent = TherapyEvent(
        timestamp = timestamp,
        type = type,
        glucoseUnit = GlucoseUnit.MGDL,
        interfaceIDs_backing = InterfaceIDs()
    )
}
