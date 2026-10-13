package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ProfileSwitchDao
import app.aaps.database.entities.ProfileSwitch
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

class InvalidateProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var profileSwitchDao: ProfileSwitchDao

    @BeforeEach
    fun setup() {
        profileSwitchDao = mock()
        database = mock()
        whenever(database.profileSwitchDao).thenReturn(profileSwitchDao)
    }

    @Test
    fun `invalidates valid profile switch`() {
        val ps = createProfileSwitch(id = 1, isValid = true)

        whenever(profileSwitchDao.findById(1)).thenReturn(ps)

        val transaction = InvalidateProfileSwitchTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(ps.isValid).isFalse()
        assertThat(result.invalidated).hasSize(1)

        verify(profileSwitchDao).updateExistingEntry(ps)
    }

    @Test
    fun `does not update already invalid profile switch`() {
        val ps = createProfileSwitch(id = 1, isValid = false)

        whenever(profileSwitchDao.findById(1)).thenReturn(ps)

        val transaction = InvalidateProfileSwitchTransaction(id = 1)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()

        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `throws exception when profile switch not found`() {
        whenever(profileSwitchDao.findById(999)).thenReturn(null)

        val transaction = InvalidateProfileSwitchTransaction(id = 999)
        transaction.database = database

        try {
            transaction.run()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("There is no such ProfileSwitch")
        }
    }

    private fun createProfileSwitch(
        id: Long,
        isValid: Boolean
    ): ProfileSwitch = ProfileSwitch(
        timestamp = System.currentTimeMillis(),
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        profileName = "Test",
        timeshift = 0,
        percentage = 100,
        duration = 0,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L)
    ).also { it.id = id }
}
