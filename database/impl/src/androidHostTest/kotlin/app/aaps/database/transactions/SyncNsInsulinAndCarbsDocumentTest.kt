package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.daos.CarbsDao
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * One Nightscout document with insulin and carbs gives a bolus and carbs that carry the same
 * Nightscout id. The two records live in different tables. These tests pin that each sync
 * transaction looks in its own table only, so the shared id is no clash.
 */
class SyncNsInsulinAndCarbsDocumentTest {

    private val nsId = "meal-1"
    private val timestamp = 1000L

    private lateinit var database: DelegatedAppDatabase
    private lateinit var bolusDao: BolusDao
    private lateinit var carbsDao: CarbsDao

    @BeforeEach
    fun setup() {
        bolusDao = mock()
        carbsDao = mock()
        database = mock()
        whenever(database.bolusDao).thenReturn(bolusDao)
        whenever(database.carbsDao).thenReturn(carbsDao)
    }

    private suspend fun syncBolus(bolus: Bolus) =
        SyncNsBolusTransaction(listOf(bolus)).also { it.database = database }.run()

    private suspend fun syncCarbs(carbs: Carbs) =
        SyncNsCarbsTransaction(listOf(carbs), nsClientMode = false).also { it.database = database }.run()

    @Test
    fun `a bolus and carbs with one Nightscout id are both inserted`() = runTest {
        val bolus = bolus()
        val carbs = carbs()
        whenever(bolusDao.getByNSId(nsId)).thenReturn(null)
        whenever(bolusDao.findByTimestamp(timestamp)).thenReturn(null)
        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        assertThat(syncBolus(bolus).inserted).containsExactly(bolus)
        assertThat(syncCarbs(carbs).inserted).containsExactly(carbs)

        verify(bolusDao).insertNewEntry(bolus)
        verify(carbsDao).insertNewEntry(carbs)
    }

    /** The bolus in the bolus table must not make the carbs look known, and the other way round. */
    @Test
    fun `the carbs are inserted when only the bolus of the document is known`() = runTest {
        val carbs = carbs()
        whenever(bolusDao.getByNSId(nsId)).thenReturn(bolus(id = 1))
        whenever(carbsDao.getByNSId(nsId)).thenReturn(null)
        whenever(carbsDao.findByTimestamp(timestamp)).thenReturn(null)

        assertThat(syncCarbs(carbs).inserted).containsExactly(carbs)

        verifyNoInteractions(bolusDao)
    }

    @Test
    fun `reading the document again inserts nothing`() = runTest {
        whenever(bolusDao.getByNSId(nsId)).thenReturn(bolus(id = 1))
        whenever(carbsDao.getByNSId(nsId)).thenReturn(carbs(id = 1))

        val bolusResult = syncBolus(bolus())
        val carbsResult = syncCarbs(carbs())

        assertThat(bolusResult.inserted).isEmpty()
        assertThat(bolusResult.updated).isEmpty()
        assertThat(carbsResult.inserted).isEmpty()
        assertThat(carbsResult.updated).isEmpty()
        verify(bolusDao, never()).insertNewEntry(any())
        verify(carbsDao, never()).insertNewEntry(any())
    }

    @Test
    fun `a deleted document marks the bolus and the carbs as not valid`() = runTest {
        val knownBolus = bolus(id = 1)
        val knownCarbs = carbs(id = 1)
        whenever(bolusDao.getByNSId(nsId)).thenReturn(knownBolus)
        whenever(carbsDao.getByNSId(nsId)).thenReturn(knownCarbs)

        assertThat(syncBolus(bolus(isValid = false)).invalidated).containsExactly(knownBolus)
        assertThat(syncCarbs(carbs(isValid = false)).invalidated).containsExactly(knownCarbs)

        assertThat(knownBolus.isValid).isFalse()
        assertThat(knownCarbs.isValid).isFalse()
        verify(bolusDao).updateExistingEntry(knownBolus)
        verify(carbsDao).updateExistingEntry(knownCarbs)
    }

    private fun bolus(id: Long = 0, isValid: Boolean = true): Bolus = Bolus(
        timestamp = timestamp,
        amount = 3.0,
        type = Bolus.Type.NORMAL,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L, 1.0)
    ).also { it.id = id }

    private fun carbs(id: Long = 0, isValid: Boolean = true): Carbs = Carbs(
        timestamp = timestamp,
        amount = 40.0,
        duration = 0L,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}
