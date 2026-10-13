package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.ExtendedBolusDao
import app.aaps.database.entities.ExtendedBolus
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

class SyncNsExtendedBolusTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var extendedBolusDao: ExtendedBolusDao

    @BeforeEach
    fun setup() {
        extendedBolusDao = mock()
        database = mock()
        whenever(database.extendedBolusDao).thenReturn(extendedBolusDao)
    }

    @Test
    fun `inserts new when nsId not found and no active bolus`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L, amount = 1.0)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(1000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.updatedNsId).isEmpty()

        verify(extendedBolusDao).insertNewEntry(eb)
    }

    @Test
    fun `updates nsId when active bolus at same timestamp`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", timestamp = 1000L, duration = 60_000L, amount = 5.0)
        val existing = createExtendedBolus(id = 1, nsId = null, timestamp = 999L, duration = 60_000L, amount = 5.0)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(1000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedNsId).hasSize(1)
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-123")

        verify(extendedBolusDao).updateExistingEntry(existing)
    }

    @Test
    fun `ends running bolus with proportional amount and inserts new`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", timestamp = 31_000L, duration = 60_000L, amount = 5.0)
        val existing = createExtendedBolus(id = 1, nsId = null, timestamp = 1000L, duration = 60_000L, amount = 6.0)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(31_000L)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.ended).hasSize(1)
        assertThat(existing.end).isEqualTo(31_000L)
        // Amount should be proportionally reduced: (31000-1000)/60000 = 0.5, so 6.0 * 0.5 = 3.0
        assertThat(existing.amount).isWithin(0.1).of(3.0)
        assertThat(result.inserted).hasSize(1)

        verify(extendedBolusDao).updateExistingEntry(existing)
        verify(extendedBolusDao).insertNewEntry(eb)
    }

    @Test
    fun `invalidates when valid becomes invalid`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", duration = 60_000L, amount = 5.0, isValid = false)
        val existing = createExtendedBolus(id = 1, nsId = "ns-123", duration = 60_000L, amount = 5.0, isValid = true)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(existing.isValid).isFalse()
    }

    @Test
    fun `updates duration to shorter and amount in NS client mode`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", duration = 30_000L, amount = 10.0)
        val existing = createExtendedBolus(id = 1, nsId = "ns-123", duration = 60_000L, amount = 5.0)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).hasSize(1)
        assertThat(existing.duration).isEqualTo(30_000L)
        assertThat(existing.amount).isEqualTo(10.0)
    }

    @Test
    fun `does not update duration to longer in NS client mode`() {
        val eb = createExtendedBolus(id = 0, nsId = "ns-123", duration = 120_000L, amount = 10.0)
        val existing = createExtendedBolus(id = 1, nsId = "ns-123", duration = 60_000L, amount = 5.0)

        whenever(extendedBolusDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb), nsClientMode = true)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updatedDuration).isEmpty()
        assertThat(existing.duration).isEqualTo(60_000L)
        assertThat(existing.amount).isEqualTo(5.0)
    }

    @Test
    fun `updates nsId when composite key matches but nsId not in DB`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"
        val nsId = "ns-123"

        val existing = createExtendedBolus(
            id = 1,
            nsId = null,
            timestamp = 1000L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createExtendedBolus(
            id = 0,
            nsId = nsId,
            timestamp = 1000L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(extendedBolusDao.findByNSId(nsId)).thenReturn(null)
        whenever(extendedBolusDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(1000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsExtendedBolusTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)
        assertThat(result.updatedNsId[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()

        verify(extendedBolusDao).updateExistingEntry(existing)
        verify(extendedBolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `inserts both records when same pumpId but different pumpType`() {
        val pumpId = 12345L

        val eb1 = createExtendedBolus(
            id = 0,
            nsId = "ns-1",
            timestamp = 1000L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = pumpId,
            pumpType = InterfaceIDs.PumpType.DANA_I,
            pumpSerial = "DANA-ABC"
        )

        val eb2 = createExtendedBolus(
            id = 0,
            nsId = "ns-2",
            timestamp = 2000L,
            duration = 60_000L,
            amount = 3.0,
            pumpId = pumpId,  // SAME pumpId
            pumpType = InterfaceIDs.PumpType.MEDTRONIC_522_722,  // DIFFERENT type
            pumpSerial = "MEDTRONIC-XYZ"
        )

        whenever(extendedBolusDao.findByNSId("ns-1")).thenReturn(null)
        whenever(extendedBolusDao.findByNSId("ns-2")).thenReturn(null)
        whenever(extendedBolusDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.DANA_I, "DANA-ABC")).thenReturn(null)
        whenever(extendedBolusDao.findByPumpIds(pumpId, InterfaceIDs.PumpType.MEDTRONIC_522_722, "MEDTRONIC-XYZ")).thenReturn(null)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(1000L)).thenReturn(Maybe.empty())
        whenever(extendedBolusDao.getExtendedBolusActiveAt(2000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsExtendedBolusTransaction(listOf(eb1, eb2), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)  // Both inserted, NO false deduplication

        verify(extendedBolusDao).insertNewEntry(eb1)
        verify(extendedBolusDao).insertNewEntry(eb2)
    }

    @Test
    fun `ignores duplicate NS record when composite key has different nsId`() {
        val pumpId = 12345L
        val pumpType = InterfaceIDs.PumpType.DANA_I
        val pumpSerial = "ABC123"

        val existing = createExtendedBolus(
            id = 1,
            nsId = "ns-OLD",  // Already has DIFFERENT nsId
            timestamp = 1000L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        val incoming = createExtendedBolus(
            id = 0,
            nsId = "ns-NEW",  // MongoDB created duplicate with new _id
            timestamp = 1000L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )

        whenever(extendedBolusDao.findByNSId("ns-NEW")).thenReturn(null)
        whenever(extendedBolusDao.findByPumpIds(pumpId, pumpType, pumpSerial)).thenReturn(existing)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(1000L)).thenReturn(Maybe.empty())

        val transaction = SyncNsExtendedBolusTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should IGNORE duplicate NS record
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo("ns-OLD")  // NOT overwritten
        assertThat(result.updatedNsId).isEmpty()  // NOT added to updated list
        assertThat(result.inserted).isEmpty()  // NOT inserted as new

        verify(extendedBolusDao, never()).updateExistingEntry(any())
        verify(extendedBolusDao, never()).insertNewEntry(any())
    }

    @Test
    fun `falls back to active check when partial pump data is null`() {
        val nsId = "ns-123"
        val timestamp = 1000L

        val existing = createExtendedBolus(
            id = 1,
            nsId = null,
            timestamp = 999L,
            duration = 60_000L,
            amount = 5.0,
            pumpId = null,  // Partial/missing pump data
            pumpType = null,
            pumpSerial = null
        )

        val incoming = createExtendedBolus(
            id = 0,
            nsId = nsId,
            timestamp = timestamp,
            duration = 60_000L,
            amount = 5.0,
            pumpId = 12345L,  // Has pumpId
            pumpType = null,  // But pumpType is null
            pumpSerial = null  // And pumpSerial is null
        )

        whenever(extendedBolusDao.findByNSId(nsId)).thenReturn(null)
        // Composite key check should NOT be called (null check fails)
        whenever(extendedBolusDao.getExtendedBolusActiveAt(timestamp)).thenReturn(Maybe.just(existing))

        val transaction = SyncNsExtendedBolusTransaction(listOf(incoming), nsClientMode = false)
        transaction.database = database
        val result = transaction.run()

        // Should fall back to active extended bolus check
        assertThat(existing.interfaceIDs.nightscoutId).isEqualTo(nsId)
        assertThat(result.updatedNsId).hasSize(1)

        verify(extendedBolusDao, never()).findByPumpIds(any(), any(), any())  // NOT called
        verify(extendedBolusDao).getExtendedBolusActiveAt(timestamp)  // Fallback used
        verify(extendedBolusDao).updateExistingEntry(existing)
    }

    private fun createExtendedBolus(
        id: Long,
        nsId: String?,
        timestamp: Long = System.currentTimeMillis(),
        duration: Long,
        amount: Double,
        isValid: Boolean = true,
        pumpId: Long? = null,
        pumpType: InterfaceIDs.PumpType? = null,
        pumpSerial: String? = null
    ): ExtendedBolus = ExtendedBolus(
        timestamp = timestamp,
        amount = amount,
        duration = duration,
        isEmulatingTempBasal = false,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(
            nightscoutId = nsId,
            pumpId = pumpId,
            pumpType = pumpType,
            pumpSerial = pumpSerial
        )
    ).also { it.id = id }
}
