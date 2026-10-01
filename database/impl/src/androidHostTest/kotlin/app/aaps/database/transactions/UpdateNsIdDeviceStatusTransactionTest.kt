package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.DeviceStatusDao
import app.aaps.database.entities.DeviceStatus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UpdateNsIdDeviceStatusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var deviceStatusDao: DeviceStatusDao

    @BeforeEach
    fun setup() {
        deviceStatusDao = mock()
        database = mock()
        whenever(database.deviceStatusDao).thenReturn(deviceStatusDao)
    }

    @Test
    fun `updates NS ID when different`() = runTest {
        val newNsId = "new-ns-123"
        val current = createDeviceStatus(id = 1, nsId = "old-ns")
        val update = createDeviceStatus(id = 1, nsId = newNsId)

        whenever(deviceStatusDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdDeviceStatusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(current.interfaceIDs.nightscoutId).isEqualTo(newNsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(deviceStatusDao).update(current)
    }

    @Test
    fun `does not update when NS ID is same`() = runTest {
        val sameNsId = "same-ns"
        val current = createDeviceStatus(id = 1, nsId = sameNsId)
        val update = createDeviceStatus(id = 1, nsId = sameNsId)

        whenever(deviceStatusDao.findById(1)).thenReturn(current)

        val transaction = UpdateNsIdDeviceStatusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(deviceStatusDao, never()).update(any())
    }

    @Test
    fun `skips when device status not found`() = runTest {
        val update = createDeviceStatus(id = 999, nsId = "new-ns")

        whenever(deviceStatusDao.findById(999)).thenReturn(null)

        val transaction = UpdateNsIdDeviceStatusTransaction(listOf(update))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).isEmpty()

        verify(deviceStatusDao, never()).update(any())
    }

    private fun createDeviceStatus(
        id: Long,
        nsId: String?
    ): DeviceStatus = DeviceStatus(
        timestamp = System.currentTimeMillis(),
        device = "TestDevice",
        pump = null,
        enacted = null,
        suggested = null,
        iob = null,
        uploaderBattery = 100,
        configuration = null,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
        isCharging = null
    ).also { it.id = id }
}
