package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.GlucoseValueDao
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InvalidateGlucoseValueTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var glucoseValueDao: GlucoseValueDao

    @BeforeEach
    fun setup() {
        glucoseValueDao = mock()
        database = mock()
        whenever(database.glucoseValueDao).thenReturn(glucoseValueDao)
    }

    @Test
    fun `invalidates valid glucose value`() = runTest {
        val gv = createGlucoseValue(id = 1, isValid = true)

        whenever(glucoseValueDao.findById(1)).thenReturn(gv)

        val transaction = InvalidateGlucoseValueTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(gv.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(glucoseValueDao).updateExistingEntry(gv)
    }

    @Test
    fun `does not update already invalid glucose value`() = runTest {
        val gv = createGlucoseValue(id = 1, isValid = false)

        whenever(glucoseValueDao.findById(1)).thenReturn(gv)

        val transaction = InvalidateGlucoseValueTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(glucoseValueDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when glucose value not found`() = runTest {
        whenever(glucoseValueDao.findById(999)).thenReturn(null)

        val transaction = InvalidateGlucoseValueTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such GlucoseValue")
        }
    }

    private fun createGlucoseValue(
        id: Long,
        isValid: Boolean
    ): GlucoseValue = GlucoseValue(
        timestamp = System.currentTimeMillis(),
        value = 120.0,
        raw = 120.0,
        noise = 0.0,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs()
    ).also { it.id = id }
}
