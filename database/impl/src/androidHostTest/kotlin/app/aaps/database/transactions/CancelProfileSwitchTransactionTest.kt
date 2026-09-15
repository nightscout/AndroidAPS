package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ProfileSwitchDao
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.data.GlucoseUnit
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CancelProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var profileSwitchDao: ProfileSwitchDao

    @BeforeEach
    fun setup() {
        profileSwitchDao = mock()
        database = mock()
        whenever(database.profileSwitchDao).thenReturn(profileSwitchDao)
    }

    @Test
    fun `cuts active temporary profile switch`() = runTest {
        val timestamp = 31_000L
        val current = createProfileSwitch(id = 7, timestamp = 1_000L, duration = 60_000L)
        whenever(profileSwitchDao.findById(7)).thenReturn(current)

        val transaction = CancelProfileSwitchTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        verify(profileSwitchDao).updateExistingEntry(current)
    }

    @Test
    fun `cuts permanent profile switch (duration=0)`() = runTest {
        val timestamp = 31_000L
        val current = createProfileSwitch(id = 7, timestamp = 1_000L, duration = 0L)
        whenever(profileSwitchDao.findById(7)).thenReturn(current)

        val transaction = CancelProfileSwitchTransaction(id = 7, timestamp = timestamp)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(current.end).isEqualTo(timestamp)
        assertThat(current.duration).isEqualTo(timestamp - 1_000L)
        verify(profileSwitchDao).updateExistingEntry(current)
    }

    @Test
    fun `does nothing when id is missing`() = runTest {
        whenever(profileSwitchDao.findById(99)).thenReturn(null)

        val transaction = CancelProfileSwitchTransaction(id = 99, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row is invalid`() = runTest {
        val current = createProfileSwitch(id = 7, timestamp = 1_000L, duration = 60_000L, isValid = false)
        whenever(profileSwitchDao.findById(7)).thenReturn(current)

        val transaction = CancelProfileSwitchTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row started at or after timestamp`() = runTest {
        val current = createProfileSwitch(id = 7, timestamp = 31_000L, duration = 60_000L)
        whenever(profileSwitchDao.findById(7)).thenReturn(current)

        val transaction = CancelProfileSwitchTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `does nothing when row already finished`() = runTest {
        // row: started 1000, lasted 5000ms → ended at 6000. Cancel attempted at 31000.
        val current = createProfileSwitch(id = 7, timestamp = 1_000L, duration = 5_000L)
        whenever(profileSwitchDao.findById(7)).thenReturn(current)

        val transaction = CancelProfileSwitchTransaction(id = 7, timestamp = 31_000L)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    private fun createProfileSwitch(
        id: Long,
        timestamp: Long,
        duration: Long,
        isValid: Boolean = true
    ): ProfileSwitch = ProfileSwitch(
        timestamp = timestamp,
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        profileName = "Test",
        timeshift = 0,
        percentage = 100,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(),
        insulinConfiguration = InsulinConfiguration("some", 600_000L, 60_000L, 1.0),
        isValid = isValid
    ).also { it.id = id }
}
