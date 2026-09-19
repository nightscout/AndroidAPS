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

class UpdateNsIdGlucoseValueTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var glucoseValueDao: GlucoseValueDao

    @BeforeEach
    fun setup() {
        glucoseValueDao = mock()
        database = mock()
        whenever(database.glucoseValueDao).thenReturn(glucoseValueDao)
    }

    @Test
    fun `updates NS ID when different`() = runTest {
        val newNsId = "new-ns-123"
        val current = createGlucoseValue(id = 1, nsId = "old-ns")
        val update = createGlucoseValue(id = 1, nsId = newNsId)

        whenever(glucoseValueDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdGlucoseValueTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(glucoseValueDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() = runTest {
        val sameNsId = "same-ns"
        val current = createGlucoseValue(id = 1, nsId = sameNsId)
        val update = createGlucoseValue(id = 1, nsId = sameNsId)

        whenever(glucoseValueDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdGlucoseValueTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(glucoseValueDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when glucose value not found`() = runTest {
        val update = createGlucoseValue(id = 999, nsId = "new-ns")

        whenever(glucoseValueDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdGlucoseValueTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(glucoseValueDao, never()).updateExistingEntry(any())
    }

    private fun createGlucoseValue(
        id: Long,
        nsId: String?
    ): GlucoseValue = GlucoseValue(
        timestamp = System.currentTimeMillis(),
        value = 120.0,
        raw = 120.0,
        noise = 0.0,
        trendArrow = GlucoseValue.TrendArrow.FLAT,
        sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}
