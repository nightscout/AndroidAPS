package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.EffectiveProfileSwitchDao
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdEffectiveProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    @BeforeEach
    fun setup() {
        effectiveProfileSwitchDao = mock()
        database = mock()
        whenever(database.effectiveProfileSwitchDao).thenReturn(effectiveProfileSwitchDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createEffectiveProfileSwitch(id = 1, nsId = "old-ns")
        val update = createEffectiveProfileSwitch(id = 1, nsId = newNsId)

        whenever(effectiveProfileSwitchDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdEffectiveProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(effectiveProfileSwitchDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createEffectiveProfileSwitch(id = 1, nsId = sameNsId)
        val update = createEffectiveProfileSwitch(id = 1, nsId = sameNsId)

        whenever(effectiveProfileSwitchDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdEffectiveProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(effectiveProfileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when effective profile switch not found`() {
        val update = createEffectiveProfileSwitch(id = 999, nsId = "new-ns")

        whenever(effectiveProfileSwitchDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdEffectiveProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(effectiveProfileSwitchDao, never()).updateExistingEntry(any())
    }

    private fun createEffectiveProfileSwitch(
        id: Long,
        nsId: String?
    ): EffectiveProfileSwitch = EffectiveProfileSwitch(
        timestamp = System.currentTimeMillis(),
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        originalProfileName = "Test",
        originalCustomizedName = "Test",
        originalTimeshift = 0,
        originalPercentage = 100,
        originalDuration = 0,
        originalEnd = 0,
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L),
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId)
    ).also { it.id = id }
}
