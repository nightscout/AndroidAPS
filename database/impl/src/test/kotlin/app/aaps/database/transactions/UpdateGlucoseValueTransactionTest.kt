package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.GlucoseValueDao
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateGlucoseValueTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var glucoseValueDao: GlucoseValueDao

    @BeforeEach
    fun setup() {
        glucoseValueDao = mock()
        database = mock()
        whenever(database.glucoseValueDao).thenReturn(glucoseValueDao)
    }

    @Test
    fun `updates glucose value`() = runTest {
        val glucoseValue = createGlucoseValue(value = 120.0)

        val transaction = UpdateGlucoseValueTransaction(glucoseValue)
        transaction.database = database
        transaction.run()

        verify(glucoseValueDao).updateExistingEntry(glucoseValue)
    }

    private fun createGlucoseValue(value: Double): GlucoseValue = GlucoseValue(
        timestamp = System.currentTimeMillis(),
        value = value,
        raw = value,
        noise = null,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
        interfaceIDs_backing = InterfaceIDs()
    )
}
