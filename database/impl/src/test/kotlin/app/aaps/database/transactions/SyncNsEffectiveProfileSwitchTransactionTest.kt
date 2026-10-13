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
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsEffectiveProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    @BeforeEach
    fun setup() {
        effectiveProfileSwitchDao = mock()
        database = mock()
        whenever(database.effectiveProfileSwitchDao).thenReturn(effectiveProfileSwitchDao)
    }

    @Test
    fun `inserts new when nsId not found and no timestamp match`() {
        val eps = createEffectiveProfileSwitch(id = 0, nsId = "ns-123", timestamp = 1000L)

        whenever(effectiveProfileSwitchDao.findByNSId("ns-123")).thenReturn(null)
        whenever(effectiveProfileSwitchDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsEffectiveProfileSwitchTransaction(listOf(eps))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(effectiveProfileSwitchDao).insertNewEntry(eps)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val eps = createEffectiveProfileSwitch(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createEffectiveProfileSwitch(id = 1, nsId = null, timestamp = 1000L)

        whenever(effectiveProfileSwitchDao.findByNSId("ns-123")).thenReturn(null)
        whenever(effectiveProfileSwitchDao.findByTimestamp(1000L)).thenReturn(existing)

        val transaction = SyncNsEffectiveProfileSwitchTransaction(listOf(eps))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(effectiveProfileSwitchDao).updateExistingEntry(existing)
    }

    @Test
    fun `invalidates when valid becomes invalid`() {
        val eps = createEffectiveProfileSwitch(id = 0, nsId = "ns-123", isValid = false)
        val existing = createEffectiveProfileSwitch(id = 1, nsId = "ns-123", isValid = true)

        whenever(effectiveProfileSwitchDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsEffectiveProfileSwitchTransaction(listOf(eps))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()
    }

    private fun createEffectiveProfileSwitch(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        isValid: Boolean = true
    ): EffectiveProfileSwitch = EffectiveProfileSwitch(
        timestamp = timestamp,
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
        insulinConfiguration = InsulinConfiguration("Test", 0, 0),
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        isValid = isValid
    ).also { it.id = id }
}
