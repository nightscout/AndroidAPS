package app.aaps.pump.medtronic.data

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.common.sync.PumpDbEntryTBR
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers [MedtronicHistoryData.endRunningTbr]: which temp basal a confirmed cancel ends in AAPS.
 *
 * Seen on a Medtronic 522: a cancel ended the temp basal from 21:07 (last read from history), which had
 * already been replaced by one AAPS set at 21:15. The 21:07 one grew from 8 to 11 minutes, and the 21:15
 * one stayed open in AAPS until the next history read.
 */
class MedtronicCancelTbrUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus

    private val serial = "678734"
    private val cancelAt = 1_000_000_000L
    private val minute = 60_000L

    private fun sut() = MedtronicHistoryData(
        aapsLogger, preferences, medtronicUtil, decoder, medtronicPumpStatus,
        pumpSync, pumpSyncStorage, notificationManager, profileUtil
    )

    private fun tbr(date: Long, rate: Double, temporaryId: Long = 0L, pumpId: Long? = null) = PumpDbEntryTBR(
        temporaryId = temporaryId, date = date, pumpType = PumpType.MEDTRONIC_522_722, serialNumber = serial,
        pumpId = pumpId, rate = rate, isAbsolute = true, durationInSeconds = 30 * 60, tbrType = PumpSync.TemporaryBasalType.NORMAL
    )

    /** The temp basal AAPS shows as running. */
    private fun aapsRuns(timestamp: Long, pumpId: Long?) = runBlocking {
        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = PumpSync.PumpState.TemporaryBasal(
                    timestamp = timestamp, duration = 30 * minute, rate = 1.0, isAbsolute = true,
                    type = PumpSync.TemporaryBasalType.NORMAL, id = 1L, pumpId = pumpId,
                    pumpType = PumpType.MEDTRONIC_522_722, pumpSerial = serial
                ),
                extendedBolus = null, bolus = null, profile = null, serialNumber = serial
            )
        )
    }

    @Test
    fun aTempBasalSetByAapsIsEndedByItsTemporaryIdAndNotTheOlderOneFromHistory() = runBlocking {
        val fromHistory = tbr(date = cancelAt -11 * minute, rate = 0.5, pumpId = 4242L)
        val setByAaps = tbr(date = cancelAt -3 * minute, rate = 1.0, temporaryId = 777L)
        whenever(medtronicPumpStatus.runningTBR).thenReturn(fromHistory)
        whenever(pumpSyncStorage.getTBRs()).thenReturn(mutableListOf(setByAaps))
        whenever(pumpSync.syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true)
        aapsRuns(timestamp = setByAaps.date, pumpId = null)

        assertThat(sut().endRunningTbr(cancelAt)).isTrue()

        verify(pumpSync).syncTemporaryBasalWithTempId(
            eq(setByAaps.date), eq(PumpRate(1.0)), eq(3 * minute), eq(true), eq(777L),
            eq(PumpSync.TemporaryBasalType.NORMAL), eq(null), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        verify(medtronicPumpStatus).runningTBR = null
    }

    @Test
    fun aTempBasalReadFromHistoryIsEndedByItsPumpId() = runBlocking {
        val fromHistory = tbr(date = cancelAt -10 * minute, rate = 0.5, pumpId = 4242L)
        whenever(medtronicPumpStatus.runningTBR).thenReturn(fromHistory)
        whenever(pumpSyncStorage.getTBRs()).thenReturn(mutableListOf())
        aapsRuns(timestamp = fromHistory.date, pumpId = 4242L)

        assertThat(sut().endRunningTbr(cancelAt)).isTrue()

        verify(pumpSync).syncTemporaryBasalWithPumpId(
            eq(fromHistory.date), eq(PumpRate(0.5)), eq(10 * minute), eq(true),
            eq(PumpSync.TemporaryBasalType.NORMAL), eq(4242L), eq(PumpType.MEDTRONIC_522_722), eq(serial)
        )
        verify(medtronicPumpStatus).runningTBR = null
    }

    /** A temp basal from history that AAPS no longer runs must not be made longer. */
    @Test
    fun anOlderTempBasalFromHistoryIsNotTouched() = runBlocking {
        val fromHistory = tbr(date = cancelAt -11 * minute, rate = 0.5, pumpId = 4242L)
        whenever(medtronicPumpStatus.runningTBR).thenReturn(fromHistory)
        whenever(pumpSyncStorage.getTBRs()).thenReturn(mutableListOf())
        aapsRuns(timestamp = cancelAt -3 * minute, pumpId = 4343L)

        assertThat(sut().endRunningTbr(cancelAt)).isFalse()

        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        verify(pumpSync, never()).syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        verify(medtronicPumpStatus).runningTBR = null
    }

    @Test
    fun nothingIsEndedWhenAapsRunsNoTempBasal() = runBlocking {
        whenever(medtronicPumpStatus.runningTBR).thenReturn(tbr(date = cancelAt -10 * minute, rate = 0.5, pumpId = 4242L))
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(null, null, null, null, serial))

        assertThat(sut().endRunningTbr(cancelAt)).isFalse()

        verify(pumpSync, never()).syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())
        verify(pumpSync, never()).syncTemporaryBasalWithTempId(any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        verify(medtronicPumpStatus).runningTBR = null
    }
}
