package app.aaps.pump.medtronic.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.utils.DateTimeUtil
import app.aaps.pump.common.sync.PumpDbEntryTBR
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.data.dto.TempBasalPair
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Which TBR from pump history gets which temp-id entry (#5271).
 *
 * The times are from a Medtronic 522 where the bug was reproduced: TBR A at 22:21:10 was synced by the read at
 * 22:21:37, cancelled at 22:22:00, and TBR B was set at 22:22:11 (pump time 22:22:14). The next read added A again
 * to limit it with the cancel, matched A by time to B's entry, and wrote A into B's row. A then had two rows with
 * the same pumpId, and B was inserted again as a new row.
 */
class MedtronicTbrMatchingUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus

    private val serial = "678734"
    private val tempIdOfB = 20261013222211L

    private fun sut() = MedtronicHistoryData(
        aapsLogger, preferences, medtronicUtil, decoder, medtronicPumpStatus,
        pumpSync, pumpSyncStorage, notificationManager, profileUtil
    )

    private fun tbr(atechDateTime: Long, rate: Double, minutes: Int) = PumpHistoryEntry().also {
        // The stamp first: setEntryType derives the pumpId from it.
        it.atechDateTime = atechDateTime
        it.setEntryType(MedtronicDeviceType.Medtronic_522, PumpHistoryEntryType.TempBasalCombined)
        it.addDecodedData("Object", TempBasalPair(rate, false, minutes))
    }

    /** The temp-id entry AAPS stored when it set B. */
    private val entryOfB = PumpDbEntryTBR(
        temporaryId = tempIdOfB, date = DateTimeUtil.toMillisFromATD(20261013222211L), pumpType = PumpType.MEDTRONIC_522_722,
        serialNumber = serial, pumpId = null, rate = 0.9, isAbsolute = true, durationInSeconds = 1800,
        tbrType = PumpSync.TemporaryBasalType.NORMAL
    )

    /** Newest first, the way a read delivers them. */
    private fun MedtronicHistoryData.readNew(vararg entries: PumpHistoryEntry) {
        val result = PumpHistoryResult(aapsLogger, null, null)
        result.validEntries = entries.toMutableList()
        addNewHistory(result)
    }

    @BeforeEach
    fun prepare() {
        whenever(medtronicPumpStatus.pumpType).thenReturn(PumpType.MEDTRONIC_522_722)
        whenever(medtronicPumpStatus.serialNumber).thenReturn(serial)
        whenever(pumpSyncStorage.getTBRs()).thenReturn(mutableListOf(entryOfB))
        runBlocking {
            whenever(pumpSync.syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true)
        }
    }

    @Test
    fun aTbrAddedAgainToBeLimitedDoesNotTakeTheEntryOfTheNextTbr() = runBlocking {
        val a = tbr(20261013222110L, 0.9, 30)
        val cancel = tbr(20261013222200L, 0.0, 0)
        val b = tbr(20261013222214L, 0.9, 30)
        val data = sut()
        data.allHistory.add(a)                 // synced by the read before
        data.readNew(b, cancel)

        data.processNewHistoryData()

        // A is limited by the cancel, in its own row (found by its pumpId).
        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(DateTimeUtil.toMillisFromATD(20261013222110L)), eq(PumpRate(0.9)), eq(50_000L), eq(true),
            anyOrNull(), eq(a.pumpId), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        // B gets its own entry, and it is the only one that does.
        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013222214L)), eq(PumpRate(0.9)), eq(1_800_000L), eq(true), eq(tempIdOfB),
            anyOrNull(), eq(b.pumpId), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        verify(pumpSync, times(1)).syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        Unit
    }

    private fun entry(temporaryId: Long, atechDateTime: Long, rate: Double) = PumpDbEntryTBR(
        temporaryId = temporaryId, date = DateTimeUtil.toMillisFromATD(atechDateTime), pumpType = PumpType.MEDTRONIC_522_722,
        serialNumber = serial, pumpId = null, rate = rate, isAbsolute = true, durationInSeconds = 1800,
        tbrType = PumpSync.TemporaryBasalType.NORMAL
    )

    /**
     * #1724: a temp target change made AAPS set a second TBR seconds after the first. Both entries were in the same
     * 10 s step of the old search, so it found "too many entries" and gave neither TBR its entry: each got a new row
     * and both temp-id rows stayed open for 30 minutes.
     */
    @Test
    fun twoTbrsSetSecondsApartEachGetTheirOwnEntry() = runBlocking {
        whenever(pumpSyncStorage.getTBRs()).thenReturn(
            mutableListOf(entry(1L, 20261013120000L, 0.9), entry(2L, 20261013120007L, 1.5))
        )
        val first = tbr(20261013120003L, 0.9, 30)
        val cancel = tbr(20261013120008L, 0.0, 0)
        val second = tbr(20261013120009L, 1.5, 30)
        val data = sut()
        data.readNew(second, cancel, first)

        data.processNewHistoryData()

        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120003L)), eq(PumpRate(0.9)), eq(5_000L), eq(true), eq(1L),
            anyOrNull(), eq(first.pumpId), any(), any()
        )
        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120009L)), eq(PumpRate(1.5)), eq(1_800_000L), eq(true), eq(2L),
            anyOrNull(), eq(second.pumpId), any(), any()
        )
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
        Unit
    }

    /**
     * #1724: the old search never looked further than 59 s when there were two candidates (its minute loop did not
     * widen the window). With the phone clock 65 s ahead of the pump both TBRs lost their entries. By time alone the
     * second TBR would even be closest to the first entry, so the rate must decide.
     */
    @Test
    fun entriesMoreThanAMinuteAwayAreMatchedByRate() = runBlocking {
        whenever(pumpSyncStorage.getTBRs()).thenReturn(
            mutableListOf(entry(1L, 20261013120105L, 0.9), entry(2L, 20261013120135L, 1.5))
        )
        val first = tbr(20261013120000L, 0.9, 30)
        val cancel = tbr(20261013120029L, 0.0, 0)
        val second = tbr(20261013120030L, 1.5, 30)
        val data = sut()
        data.readNew(second, cancel, first)

        data.processNewHistoryData()

        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120000L)), eq(PumpRate(0.9)), any(), eq(true), eq(1L),
            anyOrNull(), eq(first.pumpId), any(), any()
        )
        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120030L)), eq(PumpRate(1.5)), any(), eq(true), eq(2L),
            anyOrNull(), eq(second.pumpId), any(), any()
        )
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())
        Unit
    }

    /** A TBR set on the pump itself has no entry. It must not take the entry of the TBR AAPS set a minute later. */
    @Test
    fun aTbrSetOnThePumpDoesNotTakeTheEntryOfATbrSetByAaps() = runBlocking {
        val setOnPump = tbr(20261013222110L, 1.5, 30)
        val b = tbr(20261013222214L, 0.9, 30)
        val data = sut()
        data.readNew(b, setOnPump)

        data.processNewHistoryData()

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(DateTimeUtil.toMillisFromATD(20261013222110L)), eq(PumpRate(1.5)), any(), eq(true),
            anyOrNull(), eq(setOnPump.pumpId), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013222214L)), eq(PumpRate(0.9)), eq(1_800_000L), eq(true), eq(tempIdOfB),
            anyOrNull(), eq(b.pumpId), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        verify(pumpSync, times(1)).syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        Unit
    }

    /**
     * AAPS used another pump meanwhile: that ended B's row, and the pump is read again only from its new registration,
     * so the row with the temporary id is not updated. B still runs on the pump, so it is recorded by its pump id,
     * from the registration on. Before, it was lost, and AAPS showed no temp basal while the pump ran one.
     */
    @Test
    fun aTbrWhoseEntryIsRefusedIsRecordedByItsPumpId() = runBlocking {
        whenever(pumpSync.syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(false)
        val b = tbr(20261013222214L, 0.9, 30)
        val data = sut()
        data.readNew(b)

        data.processNewHistoryData()

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(DateTimeUtil.toMillisFromATD(20261013222214L)), eq(PumpRate(0.9)), eq(1_800_000L), eq(true),
            anyOrNull(), eq(b.pumpId), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        Unit
    }
}
