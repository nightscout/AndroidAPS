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

class InsertEffectiveProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    @BeforeEach
    fun setup() {
        effectiveProfileSwitchDao = mock()
        database = mock()
        whenever(database.effectiveProfileSwitchDao).thenReturn(effectiveProfileSwitchDao)
    }

    @Test
    fun `inserts effective profile switch`() {
        val eps = createEffectiveProfileSwitch()

        val transaction = InsertEffectiveProfileSwitchTransaction(eps)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(eps)

        verify(effectiveProfileSwitchDao).insertNewEntry(eps)
    }

    private fun createEffectiveProfileSwitch(): EffectiveProfileSwitch = EffectiveProfileSwitch(
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
        insulinConfiguration = InsulinConfiguration("Test", 0, 0),
        interfaceIDs_backing = InterfaceIDs()
    )
}
