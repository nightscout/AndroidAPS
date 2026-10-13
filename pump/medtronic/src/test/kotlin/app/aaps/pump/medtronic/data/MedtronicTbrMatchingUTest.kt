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
}
