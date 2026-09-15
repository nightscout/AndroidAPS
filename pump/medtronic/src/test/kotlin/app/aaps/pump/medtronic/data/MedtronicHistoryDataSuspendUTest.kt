package app.aaps.pump.medtronic.data

import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.data.dto.BolusDTO
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.defs.PumpBolusType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Covers [MedtronicHistoryData.isPumpSuspended] and [MedtronicHistoryData.hasRelevantConfigurationChanged].
 *
 * `isPumpSuspended` answers whether the pump is currently delivering. It decides from the newest
 * entry among nine relevant history types: the pump is treated as suspended unless that newest entry
 * is one of six "still running" types. Getting it wrong is not a cosmetic problem - the loop would
 * either keep counting basal insulin that the pump is not delivering, or treat a running pump as
 * stopped.
 */
class MedtronicHistoryDataSuspendUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus

    private fun sut() = MedtronicHistoryData(
        aapsLogger, preferences, medtronicUtil, decoder, medtronicPumpStatus,
        pumpSync, pumpSyncStorage, notificationManager, profileUtil
    )

    /** `atechDateTime` is the pump's packed yyyyMMddHHmmss stamp; bigger is newer. */
    private fun entry(type: PumpHistoryEntryType, atechDateTime: Long) =
        PumpHistoryEntry().also {
            // The stamp first: setEntryType derives the pumpId from it, the way a decoded entry gets one.
            it.atechDateTime = atechDateTime
            it.setEntryType(MedtronicDeviceType.Medtronic_723_Revel, type)
        }

    /** Feeds entries in through the real read path, which is what fills the "new history" list. */
    private fun MedtronicHistoryData.readNew(vararg entries: PumpHistoryEntry) {
        val result = PumpHistoryResult(aapsLogger, null, null)
        result.validEntries = entries.toMutableList()
        addNewHistory(result)
    }

    /** The six types that mean the pump is delivering. */
    private val runningTypes = listOf(
        PumpHistoryEntryType.TempBasalCombined,
        PumpHistoryEntryType.BasalProfileStart,
        PumpHistoryEntryType.Bolus,
        PumpHistoryEntryType.ResumePump,
        PumpHistoryEntryType.BatteryChange,
        PumpHistoryEntryType.Prime
    )

    /** The types that are looked at and are NOT in the running list, so they mean suspended. */
    private val stoppedTypes = listOf(
        PumpHistoryEntryType.SuspendPump,
        PumpHistoryEntryType.Rewind,
        PumpHistoryEntryType.NoDeliveryAlarm
    )

    @Test
    fun aPumpWithNoHistoryIsNotReportedAsSuspended() {
        assertThat(sut().isPumpSuspended()).isFalse()
    }

    @Test
    fun eachRunningTypeAsTheNewestEntryMeansThePumpIsDelivering() {
        for (type in runningTypes) {
            val data = sut()
            data.allHistory.add(entry(PumpHistoryEntryType.SuspendPump, 20240101_120000L))
            data.allHistory.add(entry(type, 20240101_130000L))   // newer

            assertThat(data.isPumpSuspended()).isFalse()
        }
    }

    @Test
    fun eachStoppedTypeAsTheNewestEntryMeansThePumpIsSuspended() {
        for (type in stoppedTypes) {
            val data = sut()
            data.allHistory.add(entry(PumpHistoryEntryType.Bolus, 20240101_120000L))
            data.allHistory.add(entry(type, 20240101_130000L))   // newer

            assertThat(data.isPumpSuspended()).isTrue()
        }
    }

    @Test
    fun aResumeAfterASuspendMeansThePumpIsDeliveringAgain() {
        val data = sut()
        data.allHistory.add(entry(PumpHistoryEntryType.SuspendPump, 20240101_120000L))
        data.allHistory.add(entry(PumpHistoryEntryType.ResumePump, 20240101_121500L))

        assertThat(data.isPumpSuspended()).isFalse()
    }

    @Test
    fun aSuspendAfterAResumeMeansThePumpIsStoppedAgain() {
        val data = sut()
        data.allHistory.add(entry(PumpHistoryEntryType.ResumePump, 20240101_120000L))
        data.allHistory.add(entry(PumpHistoryEntryType.SuspendPump, 20240101_121500L))

        assertThat(data.isPumpSuspended()).isTrue()
    }

    /**
     * Only the nine relevant types are considered. A newer entry of some other type must not hide a
     * suspend, or a pump stopped by the user would look like it was still delivering.
     */
    @Test
    fun anUnrelatedNewerEntryDoesNotHideASuspend() {
        val data = sut()
        data.allHistory.add(entry(PumpHistoryEntryType.SuspendPump, 20240101_120000L))
        data.allHistory.add(entry(PumpHistoryEntryType.ChangeBasalPattern, 20240101_130000L))

        assertThat(data.isPumpSuspended()).isTrue()
    }

    /** Entries that arrived in this read count too, not only the ones already stored. */
    @Test
    fun aSuspendFoundInTheNewlyReadHistoryCountsAsWell() {
        val data = sut()
        data.allHistory.add(entry(PumpHistoryEntryType.Bolus, 20240101_120000L))
        data.readNew(entry(PumpHistoryEntryType.SuspendPump, 20240101_130000L))

        assertThat(data.isPumpSuspended()).isTrue()
    }

    // ---- hasRelevantConfigurationChanged ----

    @Test
    fun noHistoryMeansNoConfigurationChange() {
        assertThat(sut().hasRelevantConfigurationChanged()).isFalse()
    }

    @Test
    fun eachConfigurationEntryTypeIsReportedAsAChange() {
        val configurationTypes = listOf(
            PumpHistoryEntryType.ChangeBasalPattern,
            PumpHistoryEntryType.ClearSettings,
            PumpHistoryEntryType.SaveSettings,
            PumpHistoryEntryType.ChangeMaxBolus,
            PumpHistoryEntryType.ChangeMaxBasal,
            PumpHistoryEntryType.ChangeTempBasalType
        )

        for (type in configurationTypes) {
            val data = sut()
            data.readNew(entry(type, 20240101_120000L))

            assertThat(data.hasRelevantConfigurationChanged()).isTrue()
        }
    }

    // ---- probe: what happens when a bolus record is read again with a different amount ----

    private fun bolus(atechDateTime: Long, delivered: Double) =
        PumpHistoryEntry().also {
            it.atechDateTime = atechDateTime
            it.setEntryType(MedtronicDeviceType.Medtronic_723_Revel, PumpHistoryEntryType.Bolus)
            it.decodedData["Object"] = BolusDTO(atechDateTime, delivered, delivered).also { dto ->
                dto.bolusType = PumpBolusType.Normal
            }
        }

    /**
     * A bolus record the pump reports again with a different amount must replace the stored one.
     *
     * This failed before `PumpHistoryEntry.hasBolusChanged` was corrected: it answered true when the
     * two records were the SAME, while `addNewHistory` takes a true answer as "this record changed,
     * keep the new one". An extended or dual-wave bolus is read while it is still being delivered,
     * so the amount really does change between reads, and the later value was dropped - the entry
     * kept the partial amount that was read first.
     */
    @Test
    fun aBolusReadAgainWithANewAmountIsUpdated() {
        val stamp = 20240101_120000L
        val data = sut()

        data.readNew(bolus(stamp, 1.0))
        data.finalizeNewHistoryRecords()
        data.readNew(bolus(stamp, 2.0))            // same pumpId, pump now reports 2.0
        data.finalizeNewHistoryRecords()

        val stored = (data.allHistory.single().decodedData["Object"] as BolusDTO).deliveredAmount
        assertThat(stored).isEqualTo(2.0)
    }

    /** An unchanged record read a second time must not be processed again, nor duplicated. */
    @Test
    fun anIdenticalReReadChangesNothing() {
        val stamp = 20240101_120000L
        val data = sut()

        data.readNew(bolus(stamp, 1.0))
        data.finalizeNewHistoryRecords()
        data.readNew(bolus(stamp, 1.0))
        data.finalizeNewHistoryRecords()

        assertThat(data.allHistory).hasSize(1)
        val stored = (data.allHistory.single().decodedData["Object"] as BolusDTO).deliveredAmount
        assertThat(stored).isEqualTo(1.0)
    }

    @Test
    fun aBolusIsNotAConfigurationChange() {
        val data = sut()
        data.readNew(entry(PumpHistoryEntryType.Bolus, 20240101_120000L))

        assertThat(data.hasRelevantConfigurationChanged()).isFalse()
    }
}
