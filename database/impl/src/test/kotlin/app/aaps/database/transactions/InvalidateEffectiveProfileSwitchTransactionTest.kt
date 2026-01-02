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

class InvalidateEffectiveProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var effectiveProfileSwitchDao: EffectiveProfileSwitchDao

    @BeforeEach
    fun setup() {
        effectiveProfileSwitchDao = mock()
        database = mock()
        whenever(database.effectiveProfileSwitchDao).thenReturn(effectiveProfileSwitchDao)
    }

    @Test
    fun `invalidates valid effective profile switch`() {
        val eps = createEffectiveProfileSwitch(id = 1, isValid = true)

        whenever(effectiveProfileSwitchDao.findById(1)).thenReturn(eps)

        val transaction = InvalidateEffectiveProfileSwitchTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(eps.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(effectiveProfileSwitchDao).updateExistingEntry(eps)
    }

    @Test
    fun `does not update already invalid effective profile switch`() {
        val eps = createEffectiveProfileSwitch(id = 1, isValid = false)

        whenever(effectiveProfileSwitchDao.findById(1)).thenReturn(eps)

        val transaction = InvalidateEffectiveProfileSwitchTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(effectiveProfileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when effective profile switch not found`() {
        whenever(effectiveProfileSwitchDao.findById(999)).thenReturn(null)

        val transaction = InvalidateEffectiveProfileSwitchTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such EffectiveProfileSwitch")
        }
    }

    private fun createEffectiveProfileSwitch(
        id: Long,
        isValid: Boolean
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
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L)
    ).also { it.id = id }
}
