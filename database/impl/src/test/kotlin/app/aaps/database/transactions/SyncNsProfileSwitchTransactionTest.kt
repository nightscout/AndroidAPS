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
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var profileSwitchDao: ProfileSwitchDao

    @BeforeEach
    fun setup() {
        profileSwitchDao = mock()
        database = mock()
        whenever(database.profileSwitchDao).thenReturn(profileSwitchDao)
    }

    @Test
    fun `inserts new profile switch when nsId not found and no timestamp match`() {
        val profileSwitch = createProfileSwitch(id = 0, nsId = "ns-123", timestamp = 1000L)

        whenever(profileSwitchDao.findByNSId("ns-123")).thenReturn(null)
        whenever(profileSwitchDao.findByTimestamp(1000L)).thenReturn(null)

        val transaction = SyncNsProfileSwitchTransaction(listOf(profileSwitch))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(profileSwitchDao).insertNewEntry(profileSwitch)
    }

    @Test
    fun `updates nsId when timestamp matches but nsId is null`() {
        val profileSwitch = createProfileSwitch(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createProfileSwitch(id = 1, nsId = null, timestamp = 1000L)

        whenever(profileSwitchDao.findByNSId("ns-123")).thenReturn(null)
        whenever(profileSwitchDao.findByTimestamp(1000L)).thenReturn(existing)

        val transaction = SyncNsProfileSwitchTransaction(listOf(profileSwitch))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")
        assertThat(result.inserted).isEmpty()

        verify(profileSwitchDao).updateExistingEntry(existing)
    }

    @Test
    fun `invalidates profile switch when valid becomes invalid`() {
        val profileSwitches = listOf(createProfileSwitch(id = 0, nsId = "ns-123", isValid = false))
        val existing = createProfileSwitch(id = 1, nsId = "ns-123", isValid = true)

        whenever(profileSwitchDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsProfileSwitchTransaction(profileSwitches)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()
        assertThat(result.inserted).isEmpty()

        verify(profileSwitchDao).updateExistingEntry(existing)
    }

    @Test
    fun `inserts new when timestamp matches but existing has different nsId`() {
        val profileSwitch = createProfileSwitch(id = 0, nsId = "ns-123", timestamp = 1000L)
        val existing = createProfileSwitch(id = 1, nsId = "other-ns", timestamp = 1000L)

        whenever(profileSwitchDao.findByNSId("ns-123")).thenReturn(null)
        whenever(profileSwitchDao.findByTimestamp(1000L)).thenReturn(existing)

        val transaction = SyncNsProfileSwitchTransaction(listOf(profileSwitch))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()

        verify(profileSwitchDao).insertNewEntry(profileSwitch)
    }

    private fun createProfileSwitch(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
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
        duration = 0,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L),
        isValid = isValid
    ).also { it.id = id }
}
