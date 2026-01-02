package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.APSResultDao
import app.aaps.database.entities.APSResult
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsertOrUpdateApsResultTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var apsResultDao: APSResultDao

    @BeforeEach
    fun setup() {
        apsResultDao = mock()
        database = mock()
        whenever(database.apsResultDao).thenReturn(apsResultDao)
    }

    @Test
    fun `inserts new APS result when id not found`() {
        val apsResult = createApsResult(id = 1, targetBG = 100.0)

        whenever(apsResultDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateApsResultTransaction(apsResult)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(apsResult)
        assertThat(result.updated).isEmpty()

        verify(apsResultDao).insertNewEntry(apsResult)
        verify(apsResultDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing APS result when id found`() {
        val apsResult = createApsResult(id = 1, targetBG = 110.0)
        val existing = createApsResult(id = 1, targetBG = 100.0)

        whenever(apsResultDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateApsResultTransaction(apsResult)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(apsResult)
        assertThat(result.inserted).isEmpty()

        verify(apsResultDao).updateExistingEntry(apsResult)
        verify(apsResultDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates target BG value`() {
        val existing = createApsResult(id = 1, targetBG = 90.0)
        val updated = createApsResult(id = 1, targetBG = 120.0)

        whenever(apsResultDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateApsResultTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(updated)

        verify(apsResultDao).updateExistingEntry(updated)
    }

    @Test
    fun `inserts APS result with rate`() {
        val apsResult = createApsResult(id = 1, targetBG = 100.0, rate = 0.85)

        whenever(apsResultDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateApsResultTransaction(apsResult)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(apsResult)

        verify(apsResultDao).insertNewEntry(apsResult)
    }

    private fun createApsResult(
        id: Long,
        targetBG: Double,
        rate: Double = 1.0
    ): APSResult = APSResult(
        timestamp = System.currentTimeMillis(),
        algorithm = APSResult.Algorithm.SMB,
        glucoseStatusJson = "{}",
        currentTempJson = "{}",
        iobDataJson = "{}",
        profileJson = "{}",
        mealDataJson = "{}",
        resultJson = JSONObject().put("rate", rate).put("targetBG", targetBG).toString(),
        autosensDataJson = null
    ).also { it.id = id }
}
