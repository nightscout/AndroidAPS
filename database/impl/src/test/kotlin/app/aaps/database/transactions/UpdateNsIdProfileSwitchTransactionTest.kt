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

class UpdateNsIdProfileSwitchTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var profileSwitchDao: ProfileSwitchDao

    @BeforeEach
    fun setup() {
        profileSwitchDao = mock()
        database = mock()
        whenever(database.profileSwitchDao).thenReturn(profileSwitchDao)
    }

    @Test
    fun `updates NS ID when different`() {
        val newNsId = "new-ns-123"
        val current = createProfileSwitch(id = 1, nsId = "old-ns")
        val update = createProfileSwitch(id = 1, nsId = newNsId)

        whenever(profileSwitchDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(profileSwitchDao).updateExistingEntry(current)
    }

    @Test
    fun `does not update when NS ID is same`() {
        val sameNsId = "same-ns"
        val current = createProfileSwitch(id = 1, nsId = sameNsId)
        val update = createProfileSwitch(id = 1, nsId = sameNsId)

        whenever(profileSwitchDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `skips when profile switch not found`() {
        val update = createProfileSwitch(id = 999, nsId = "new-ns")

        whenever(profileSwitchDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdProfileSwitchTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(profileSwitchDao, never()).updateExistingEntry(any())
    }

    private fun createProfileSwitch(
        id: Long,
        nsId: String?
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
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        insulinConfiguration = InsulinConfiguration("some", 600000L, 60000L)
    ).also { it.id = id }
}
