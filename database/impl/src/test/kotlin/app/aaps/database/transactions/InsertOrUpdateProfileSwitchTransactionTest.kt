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

class InsertOrUpdateProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var profileSwitchDao: ProfileSwitchDao

    @BeforeEach
    fun setup() {
        profileSwitchDao = mock()
        database = mock()
        whenever(database.profileSwitchDao).thenReturn(profileSwitchDao)
    }

    @Test
    fun `inserts new profile switch when id not found`() {
        val profileSwitch = createProfileSwitch(id = 1, percentage = 100)

        whenever(profileSwitchDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateProfileSwitchTransaction(profileSwitch)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(profileSwitch)
        assertThat(result.updated).isEmpty()

        verify(profileSwitchDao).insertNewEntry(profileSwitch)
        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `updates existing profile switch when id found`() {
        val profileSwitch = createProfileSwitch(id = 1, percentage = 120)
        val existing = createProfileSwitch(id = 1, percentage = 100)

        whenever(profileSwitchDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateProfileSwitchTransaction(profileSwitch)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(profileSwitch)
        assertThat(result.inserted).isEmpty()

        verify(profileSwitchDao).updateExistingEntry(profileSwitch)
        verify(profileSwitchDao, never()).insertNewEntry(any())
    }

    @Test
    fun `updates profile switch percentage`() {
        val existing = createProfileSwitch(id = 1, percentage = 100)
        val updated = createProfileSwitch(id = 1, percentage = 150)

        whenever(profileSwitchDao.findById(1)).thenReturn(existing)

        val transaction = InsertOrUpdateProfileSwitchTransaction(updated)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0].percentage).isEqualTo(150)
    }

    @Test
    fun `inserts profile switch with timeshift`() {
        val profileSwitch = createProfileSwitch(id = 1, percentage = 100, timeshift = 2)

        whenever(profileSwitchDao.findById(1)).thenReturn(null)

        val transaction = InsertOrUpdateProfileSwitchTransaction(profileSwitch)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0].timeshift).isEqualTo(2)
    }

    private fun createProfileSwitch(
        id: Long,
        percentage: Int,
        timeshift: Long = 0
    ): ProfileSwitch = ProfileSwitch(
        timestamp = System.currentTimeMillis(),
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        profileName = "Test",
        timeshift = timeshift,
        percentage = percentage,
        duration = 0,
        interfaceIDs_backing = InterfaceIDs(),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L)
    ).also { it.id = id }
}
