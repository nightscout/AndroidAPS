package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.BolusDao
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A pump change: the temp basals and boluses AAPS set that the old pump never confirmed are kept as they were set.
 * With only a temporary id they stayed unconfirmed for good, and were never uploaded to Nightscout.
 */
class ConfirmTemporaryIdsTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao
    private lateinit var bolusDao: BolusDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        bolusDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
        whenever(database.bolusDao).thenReturn(bolusDao)
    }

    private fun ids(temporaryId: Long) = InterfaceIDs(temporaryId = temporaryId, pumpType = InterfaceIDs.PumpType.MEDTRONIC_522_722, pumpSerial = "678734")

    /**
     * The Nightscout sync sends the entries with a pump id that are newer than the last one it sent. An update of
     * the old entry stores its old version, without a pump id, as the newer entry: the sync would never see it.
     */
    @Test
    fun `records with only a temporary id are stored again with it as their pump id`() = runTest {
        val temporaryBasal = TemporaryBasal(
            id = 9416, timestamp = 1000L, rate = 0.0, duration = 60_000L, type = TemporaryBasal.Type.NORMAL, isAbsolute = true,
            interfaceIDs_backing = ids(20261014090015L)
        )
        val bolus = Bolus(
            id = 500, timestamp = 2000L, amount = 1.0, type = Bolus.Type.NORMAL, interfaceIDs_backing = ids(20261014090100L),
            insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L, 1.0)
        )
        whenever(temporaryBasalDao.getWithTemporaryIdOnly()).thenReturn(listOf(temporaryBasal))
        whenever(bolusDao.getWithTemporaryIdOnly()).thenReturn(listOf(bolus))

        val transaction = ConfirmTemporaryIdsTransaction()
        transaction.database = database
        val result = transaction.run()

        val keptTemporaryBasal = argumentCaptor<TemporaryBasal>().apply { verify(temporaryBasalDao).insertNewEntry(capture()) }.firstValue
        assertThat(keptTemporaryBasal.id).isEqualTo(0L)
        assertThat(keptTemporaryBasal.timestamp).isEqualTo(1000L)
        assertThat(keptTemporaryBasal.duration).isEqualTo(60_000L)
        assertThat(keptTemporaryBasal.interfaceIDs.pumpId).isEqualTo(20261014090015L)
        assertThat(keptTemporaryBasal.interfaceIDs.pumpSerial).isEqualTo("678734")
        // Not copied: a search by the temporary id finds only the old entry.
        assertThat(keptTemporaryBasal.interfaceIDs.temporaryId).isNull()
        assertThat(keptTemporaryBasal.isValid).isTrue()

        val keptBolus = argumentCaptor<Bolus>().apply { verify(bolusDao).insertNewEntry(capture()) }.firstValue
        assertThat(keptBolus.amount).isEqualTo(1.0)
        assertThat(keptBolus.interfaceIDs.pumpId).isEqualTo(20261014090100L)
        assertThat(keptBolus.interfaceIDs.temporaryId).isNull()

        // The old entries are invalidated and keep their ids as they were.
        verify(temporaryBasalDao).updateExistingEntry(temporaryBasal)
        assertThat(temporaryBasal.isValid).isFalse()
        assertThat(temporaryBasal.interfaceIDs.pumpId).isNull()
        assertThat(temporaryBasal.interfaceIDs.temporaryId).isEqualTo(20261014090015L)
        verify(bolusDao).updateExistingEntry(bolus)
        assertThat(bolus.isValid).isFalse()

        assertThat(result.temporaryBasals).containsExactly(keptTemporaryBasal)
        assertThat(result.boluses).containsExactly(keptBolus)
    }

    @Test
    fun `nothing to keep changes nothing`() = runTest {
        whenever(temporaryBasalDao.getWithTemporaryIdOnly()).thenReturn(emptyList())
        whenever(bolusDao.getWithTemporaryIdOnly()).thenReturn(emptyList())

        val transaction = ConfirmTemporaryIdsTransaction()
        transaction.database = database
        transaction.run()

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
        verify(temporaryBasalDao, never()).insertNewEntry(any())
        verify(bolusDao, never()).updateExistingEntry(any())
        verify(bolusDao, never()).insertNewEntry(any())
    }
}
