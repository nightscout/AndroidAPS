package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.TemporaryBasalDao
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.entities.interfaces.end
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

class SyncNsTemporaryBasalTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var temporaryBasalDao: TemporaryBasalDao

    @BeforeEach
    fun setup() {
        temporaryBasalDao = mock()
        database = mock()
        whenever(database.temporaryBasalDao).thenReturn(temporaryBasalDao)
    }

    @Test
    fun `inserts new when nsId not found and no active basal`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(1000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()

        verify(temporaryBasalDao).insertNewEntry(tb)
    }

    @Test
    fun `updates nsId when active basal at same timestamp`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L)
        val existing = createTemporaryBasal(id = 1, nsId = null, timestamp = 999L, duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(1000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(temporaryBasalDao).updateExistingEntry(existing)
    }

    @Test
    fun `ends running basal and inserts new when timestamps differ`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", timestamp = 5000L, duration = 60_000L)
        val existing = createTemporaryBasal(id = 1, nsId = null, timestamp = 1000L, duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(5000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.ended).hasSize(1)
        assertThat(existing.end).isEqualTo(5000L)
        assertThat(result.inserted).hasSize(1)

        verify(temporaryBasalDao).updateExistingEntry(existing)
        verify(temporaryBasalDao).insertNewEntry(tb)
    }

    @Test
    fun `invalidates when valid becomes invalid`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", duration = 60_000L, isValid = false)
        val existing = createTemporaryBasal(id = 1, nsId = "ns-123", duration = 60_000L, isValid = true)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()
    }

    @Test
    fun `updates duration to shorter in NS client mode`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", duration = 30_000L)
        val existing = createTemporaryBasal(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).hasSize(1)
        assertThat(existing.duration).isEqualTo(30_000L)
    }

    @Test
    fun `does not update duration to longer in NS client mode`() {
        val tb = createTemporaryBasal(id = 0, nsId = "ns-123", duration = 120_000L)
        val existing = createTemporaryBasal(id = 1, nsId = "ns-123", duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(existing.duration).isEqualTo(60_000L)
    }

    @Test
    fun `finds by pump ids when nsId not found`() {
        val tb = createTemporaryBasal(
            id = 0,
            nsId = "ns-123",
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = 12345L,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "ABC123"
        )
        val existing = createTemporaryBasal(id = 1, nsId = null, duration = 60_000L)

        whenever(temporaryBasalDao.findByNSId("ns-123")).thenReturn(null)
        whenever(temporaryBasalDao.findByPumpIds(12345L, InterfaceIDs.PumpType.DANA_I, "ABC123")).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()
    }

    @Test
    fun `updates nsId when composite key matches but nsId not in DB`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val nsId = "ns-123"

        val existing = createTemporaryBasal(
            id = 1,
            nsId = null,
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createTemporaryBasal(
            id = 0,
            nsId = nsId,
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(temporaryBasalDao.findByNSId(nsId)).thenReturn(null)
        whenever(temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(temporaryBasalDao).updateExistingEntry(existing)
        verify(temporaryBasalDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts both records when same pumpId but different pumpType`() {
        val pumpId = 12345L

        val tb1 = createTemporaryBasal(
            id = 0,
            nsId = "ns-1",
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "DANA-ABC"
        )

        val tb2 = createTemporaryBasal(
            id = 0,
            nsId = "ns-2",
            timestamp = 2000L,
            duration = 60_000L,
            pumpId = pumpId,  // SAME pumpId
            pumpType = InterfaceIDs.PumpType.MEDTRONIC_522_722,  // DIFFERENT type
            pumpSerial = "MEDTRONIC-XYZ"
        )

        whenever(temporaryBasalDao.findByNSId("ns-1")).thenReturn(null)
        whenever(temporaryBasalDao.findByNSId("ns-2")).thenReturn(null)
        whenever(temporaryBasalDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.DANA_I, "DANA-ABC")).thenReturn(null)
        whenever(temporaryBasalDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.MEDTRONIC_522_722, "MEDTRONIC-XYZ")).thenReturn(null)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(1000L)).thenReturn(Maybe.empty())
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(2000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsTemporaryBasalTransaction(listOf(tb1, tb2), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)  // Both inserted, NO false deduplication

        verify(temporaryBasalDao).insertNewEntry(tb1)
        verify(temporaryBasalDao).insertNewEntry(tb2)
    }

    @Test
    fun `ignores duplicate NS record when composite key has different nsId`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"

        val existing = createTemporaryBasal(
            id = 1,
            nsId = "ns-OLD",  // Already has DIFFERENT nsId
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createTemporaryBasal(
            id = 0,
            nsId = "ns-NEW",  // MongoDB created duplicate with new _id
            timestamp = 1000L,
            duration = 60_000L,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(temporaryBasalDao.findByNSId("ns-NEW")).thenReturn(null)
        whenever(temporaryBasalDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)

        val transaction = SyncNsTemporaryBasalTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should IGNORE duplicate NS record
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-OLD")  // NOT overwritten
        assertThat(result.updatedNsId).isEmpty()  // NOT added to updated list
        assertThat(result.inserted).isEmpty()  // NOT inserted as new

        verify(temporaryBasalDao, never()).updateExistingEntry(any())
        verify(temporaryBasalDao, never()).insertNewEntry(any())
    }

    @Test
    fun `falls back to active check when partial pump data is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L

        val existing = createTemporaryBasal(
            id = 1,
            nsId = null,
            timestamp = 999L,
            duration = 60_000L,
            pumpId = null,  // Partial/missing pump data
            pumpType = null,
            pumpSerial = null
        )

        val incoming = createTemporaryBasal(
            id = 0,
            nsId = nsId,
            timestamp = timestamp,
            duration = 60_000L,
            pumpId = 12345L,  // Has pumpId
            pumpType = null,  // But pumpType is null
            pumpSerial = null  // And pumpSerial is null
        )

        whenever(temporaryBasalDao.findByNSId(nsId)).thenReturn(null)
        // Composite key check should NOT be called (null check fails)
        whenever(temporaryBasalDao.getTemporaryBasalActiveAt(timestamp)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsTemporaryBasalTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should fall back to active TBR check
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(temporaryBasalDao, never()).findByPumpIds(any(), any(), any())  // NOT called
        verify(temporaryBasalDao).getTemporaryBasalActiveAt(timestamp)  // Fallback used
        verify(temporaryBasalDao).updateExistingEntry(existing)
    }

    private fun createTemporaryBasal(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long,
        isValid: Boolean = true,
        pumpId: Long? = null,
        pumpType: InterfaceIDs.PumpType? = null,
        pumpSerial: String? = null
    ): TemporaryBasal = TemporaryBasal(
        timestamp = timestamp,
        rate = 1.5,
        duration = duration,
        type = TemporaryBasal.Type.NORMAL,
        isAbsolute = true,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(
            nightscoutId = nsId,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    ).also { it.id = id }
}
