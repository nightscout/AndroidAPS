package app.aaps.pump.medtronic.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.DateTimeUtil
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
 * A read can bring entries that are older than ones an earlier read already processed (#3755).
 *
 * The times are from the log of a Medtronic 712. The read at 10:43:10 processed the TBR of 10:41:55. The read at
 * 10:49:56 brought the entries from 10:44:54 on, but not the three from 10:43:53 to 10:44:52. The read at 10:56:41
 * brought them, and the oldest of them is a cancel. The TBR that this cancel ends was taken from the earlier reads,
 * but it was the newest one there, the TBR of 10:47:56: it ran from 10:47:56 to 10:43:53, -243 s, and AAPS showed
 * "Invalid pump history data detected" every few days.
 */
class MedtronicLateHistoryEntriesUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus

    private val serial = "272456"

    private fun sut() = MedtronicHistoryData(
        aapsLogger, preferences, medtronicUtil, decoder, medtronicPumpStatus,
        pumpSync, pumpSyncStorage, notificationManager, profileUtil
    )

    private fun entry(type: PumpHistoryEntryType, atechDateTime: Long) = PumpHistoryEntry().also {
        // The stamp first: setEntryType derives the pumpId from it.
        it.atechDateTime = atechDateTime
        it.setEntryType(MedtronicDeviceType.Medtronic_712, type)
    }

    private fun tbr(atechDateTime: Long, rate: Double, minutes: Int) = entry(PumpHistoryEntryType.TempBasalCombined, atechDateTime).also {
        it.addDecodedData("Object", TempBasalPair(rate, false, minutes))
    }

    private fun cancel(atechDateTime: Long) = tbr(atechDateTime, 0.0, 0)

    /** Newest first, the way a read delivers them. */
    private fun MedtronicHistoryData.readNew(vararg entries: PumpHistoryEntry) {
        val result = PumpHistoryResult(aapsLogger, null, null)
        result.validEntries = entries.toMutableList()
        addNewHistory(result)
    }

    private fun verifyTbr(atechDateTime: Long, rate: Double, durationMs: Long, type: PumpSync.TemporaryBasalType = PumpSync.TemporaryBasalType.NORMAL) {
        runBlocking {
            verify(pumpSync).syncTemporaryBasalWithPumpId(
                eq(DateTimeUtil.toMillisFromATD(atechDateTime)), eq(PumpRate(rate)), eq(durationMs), eq(true),
                eq(type), any(), eq(PumpType.MEDTRONIC_512_712), eq(serial)
            )
        }
    }

    private fun verifyTbrCount(count: Int, type: PumpSync.TemporaryBasalType) {
        runBlocking {
            verify(pumpSync, times(count)).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), eq(type), any(), anyOrNull(), anyOrNull())
        }
    }

    private fun verifyNoInvalidHistoryNotice() {
        verify(notificationManager, never()).post(
            eq(NotificationId.MDT_INVALID_HISTORY_DATA), any<TextRef>(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull()
        )
    }

    @BeforeEach
    fun prepare() {
        whenever(medtronicPumpStatus.pumpType).thenReturn(PumpType.MEDTRONIC_512_712)
        whenever(medtronicPumpStatus.serialNumber).thenReturn(serial)
        whenever(pumpSyncStorage.getTBRs()).thenReturn(mutableListOf())
    }

    @Test
    fun aLateCancelEndsTheTbrBeforeItAndNotTheNewestTbr() {
        val data = sut()
        // From the reads at 10:43:10 and 10:49:56
        data.allHistory.addAll(
            listOf(
                tbr(20250119104756L, 0.0, 30),
                cancel(20250119104754L),
                tbr(20250119104454L, 0.35, 30),
                tbr(20250119104155L, 0.3, 30),
                cancel(20250119104153L)
            )
        )
        data.readNew(
            tbr(20250119105200L, 0.0, 60),
            cancel(20250119105158L),
            tbr(20250119105020L, 0.3, 30),
            cancel(20250119104452L),
            tbr(20250119104355L, 0.7, 30),
            cancel(20250119104353L)
        )

        data.processNewHistoryData()

        verifyNoInvalidHistoryNotice()
        verifyTbr(20250119104155L, 0.3, 118_000L)        // ended by the late cancel at 10:43:53
        verifyTbr(20250119104355L, 0.7, 57_000L)
        verifyTbr(20250119105020L, 0.3, 98_000L)
        verifyTbr(20250119105200L, 0.0, 3_600_000L)
        verifyTbrCount(4, PumpSync.TemporaryBasalType.NORMAL)
    }

    /** No TBR from earlier reads is older than the late cancel: there is nothing for it to end, so it is dropped. */
    @Test
    fun aLateCancelWithNoTbrBeforeItIsDropped() {
        val data = sut()
        data.allHistory.add(tbr(20250119104756L, 0.0, 30))
        data.readNew(
            tbr(20250119105020L, 0.3, 30),
            cancel(20250119104353L)
        )

        data.processNewHistoryData()

        verifyNoInvalidHistoryNotice()
        verifyTbr(20250119105020L, 0.3, 1_800_000L)
        verifyTbrCount(1, PumpSync.TemporaryBasalType.NORMAL)
    }

    /**
     * The same late read for suspends: the pump was stopped 10:00-10:05 and 10:08-10:10. Before, the late resume of
     * 10:05 was paired with the newest suspend, the one of 10:20, and AAPS recorded the pump as stopped 10:05-10:08
     * and 10:10-10:20, while it was running.
     */
    @Test
    fun aLateResumeEndsTheSuspendBeforeIt() {
        val data = sut()
        // The read before had the suspend of 10:00, the next one missed the three entries from 10:05 to 10:10
        data.allHistory.addAll(
            listOf(
                entry(PumpHistoryEntryType.ResumePump, 20250119102500L),
                entry(PumpHistoryEntryType.SuspendPump, 20250119102000L),
                entry(PumpHistoryEntryType.SuspendPump, 20250119100000L)
            )
        )
        data.readNew(
            entry(PumpHistoryEntryType.ResumePump, 20250119101000L),
            entry(PumpHistoryEntryType.SuspendPump, 20250119100800L),
            entry(PumpHistoryEntryType.ResumePump, 20250119100500L)
        )

        data.processNewHistoryData()

        verifyTbr(20250119100000L, 0.0, 300_000L, PumpSync.TemporaryBasalType.PUMP_SUSPEND)
        verifyTbr(20250119100800L, 0.0, 120_000L, PumpSync.TemporaryBasalType.PUMP_SUSPEND)
        verifyTbrCount(2, PumpSync.TemporaryBasalType.PUMP_SUSPEND)
    }
}
