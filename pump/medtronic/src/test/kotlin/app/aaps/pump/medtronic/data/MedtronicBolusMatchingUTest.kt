package app.aaps.pump.medtronic.data

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.utils.DateTimeUtil
import app.aaps.pump.common.sync.PumpDbEntryBolus
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.data.dto.BolusDTO
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.PumpBolusType
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
 * Which bolus from pump history gets which temp-id entry. A bolus without its entry gets a new row by pumpId, and
 * the row AAPS wrote when it sent the bolus stays: the bolus is in the database twice and IOB is too high.
 */
class MedtronicBolusMatchingUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus

    private val serial = "678734"

    private fun sut() = MedtronicHistoryData(
        aapsLogger, preferences, medtronicUtil, decoder, medtronicPumpStatus,
        pumpSync, pumpSyncStorage, notificationManager, profileUtil
    )

    private fun bolus(atechDateTime: Long, requested: Double, delivered: Double = requested) = PumpHistoryEntry().also {
        // The stamp first: setEntryType derives the pumpId from it.
        it.atechDateTime = atechDateTime
        it.setEntryType(MedtronicDeviceType.Medtronic_522, PumpHistoryEntryType.Bolus)
        it.decodedData["Object"] = BolusDTO(atechDateTime, requested, delivered).also { dto -> dto.bolusType = PumpBolusType.Normal }
    }

    private fun entry(temporaryId: Long, atechDateTime: Long, insulin: Double, type: BS.Type) = PumpDbEntryBolus(
        temporaryId = temporaryId, date = DateTimeUtil.toMillisFromATD(atechDateTime), pumpType = PumpType.MEDTRONIC_522_722,
        serialNumber = serial, pumpId = null, insulin = insulin, carbs = 0.0, bolusType = type
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
    }

    /**
     * An SMB and a manual bolus a few seconds apart. Both entries were in the same 10 s step of the old search, so it
     * found "too many entries" and gave neither bolus its entry.
     */
    @Test
    fun twoBolusesSecondsApartEachGetTheirOwnEntry() = runBlocking {
        whenever(pumpSyncStorage.getBoluses()).thenReturn(
            mutableListOf(entry(1L, 20261013120000L, 0.3, BS.Type.SMB), entry(2L, 20261013120006L, 1.0, BS.Type.NORMAL))
        )
        val smb = bolus(20261013120002L, 0.3)
        val manual = bolus(20261013120008L, 1.0)
        val data = sut()
        data.readNew(manual, smb)

        data.processNewHistoryData()

        verify(pumpSync).syncBolusWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120002L)), eq(PumpInsulin(0.3)), eq(1L), anyOrNull(), eq(smb.pumpId), any(), any()
        )
        verify(pumpSync).syncBolusWithTempId(
            eq(DateTimeUtil.toMillisFromATD(20261013120008L)), eq(PumpInsulin(1.0)), eq(2L), anyOrNull(), eq(manual.pumpId), any(), any()
        )
        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
        Unit
    }

    /** With the phone clock 70 s ahead of the pump the old search found no entry for either bolus. */
    @Test
    fun entriesMoreThanAMinuteAwayAreMatchedByAmount() = runBlocking {
        whenever(pumpSyncStorage.getBoluses()).thenReturn(
            mutableListOf(entry(1L, 20261013120110L, 0.3, BS.Type.SMB), entry(2L, 20261013120140L, 1.0, BS.Type.NORMAL))
        )
        val smb = bolus(20261013120000L, 0.3)
        val manual = bolus(20261013120030L, 1.0)
        val data = sut()
        data.readNew(manual, smb)

        data.processNewHistoryData()

        verify(pumpSync).syncBolusWithTempId(any(), eq(PumpInsulin(0.3)), eq(1L), anyOrNull(), eq(smb.pumpId), any(), any())
        verify(pumpSync).syncBolusWithTempId(any(), eq(PumpInsulin(1.0)), eq(2L), anyOrNull(), eq(manual.pumpId), any(), any())
        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
        Unit
    }

    /**
     * A bolus read while it was still running is read again with the full amount. Its entry was used in the first
     * read, so it keeps its row (pumpId path) and must not take an entry that belongs to another bolus.
     */
    @Test
    fun aBolusReadAgainWithANewAmountDoesNotTakeAnotherEntry() = runBlocking {
        val stamp = 20261013120000L
        val data = sut()
        whenever(pumpSyncStorage.getBoluses()).thenReturn(mutableListOf(entry(1L, stamp, 1.0, BS.Type.NORMAL)))
        data.readNew(bolus(stamp, 1.0, delivered = 0.5))
        data.processNewHistoryData()
        data.finalizeNewHistoryRecords()

        whenever(pumpSyncStorage.getBoluses()).thenReturn(mutableListOf(entry(2L, 20261013120030L, 1.0, BS.Type.NORMAL)))
        val full = bolus(stamp, 1.0, delivered = 1.0)
        data.readNew(full)
        data.processNewHistoryData()

        verify(pumpSync, times(1)).syncBolusWithTempId(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        verify(pumpSync).syncBolusWithPumpId(
            eq(DateTimeUtil.toMillisFromATD(stamp)), eq(PumpInsulin(1.0)), anyOrNull(), eq(full.pumpId), any(), any()
        )
        Unit
    }
}
