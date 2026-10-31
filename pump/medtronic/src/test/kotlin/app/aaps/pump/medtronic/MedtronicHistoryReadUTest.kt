package app.aaps.pump.medtronic

import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.comm.ui.MedtronicUIComm
import app.aaps.pump.medtronic.comm.ui.MedtronicUITask
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.joda.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * #3378, #2512: a history read that failed on a page is not processed, and the last entry stays as it is. So the next
 * read asks for the same time again and gets the missing entries too. Processed without them, a temp basal was ended
 * by the wrong entry, and the missing entries came later as new and gave a temp basal a negative length.
 */
class MedtronicHistoryReadUTest : TestBaseWithProfile() {

    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var medtronicUtil: MedtronicUtil
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var medtronicHistoryData: MedtronicHistoryData
    @Mock lateinit var serviceTaskExecutor: ServiceTaskExecutor
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var rileyLinkMedtronicService: RileyLinkMedtronicService
    @Mock lateinit var medtronicUIComm: MedtronicUIComm

    private lateinit var plugin: MedtronicPumpPlugin

    @BeforeEach
    fun setup() {
        plugin = MedtronicPumpPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, medtronicUtil, MedtronicPumpStatus(preferences, rxBus, rileyLinkUtil),
            medtronicHistoryData, RileyLinkServiceData(aapsLogger, rileyLinkUtil, rxBus), serviceTaskExecutor, uiInteraction,
            notificationManager, dateUtil, pumpSync, pumpSyncStorage, decimalFormatter, pumpEnactResultProvider, bolusProgressData,
            { mock<WakeAndTuneTask>() }, { mock<ResetRileyLinkConfigurationTask>() }, blePreCheck
        )
        whenever(rileyLinkMedtronicService.medtronicUIComm).thenReturn(medtronicUIComm)
        // Both are private: the service is set when it binds, the last entry by the read before.
        setPrivateField("rileyLinkMedtronicService", rileyLinkMedtronicService)
        setPrivateField("lastPumpHistoryEntry", entry(LAST_ENTRY))
    }

    @Test
    fun `a failed read is not processed and the next read asks for the same time`() {
        readHistory(resultWith(20230420051658L).apply { readFailed = true })

        verify(medtronicHistoryData, never()).addNewHistory(any())

        readHistory(resultWith(20230420052500L))

        // 35 minutes before the last entry both times.
        assertThat(targetDates()).containsExactly(LocalDateTime(2023, 4, 20, 4, 37, 57), LocalDateTime(2023, 4, 20, 4, 37, 57))
        verify(medtronicHistoryData, times(1)).addNewHistory(any())
    }

    @Test
    fun `after a complete read the next read starts 35 minutes before its newest entry`() {
        readHistory(resultWith(20230420051658L))
        readHistory(resultWith(20230420052500L))

        assertThat(targetDates()).containsExactly(LocalDateTime(2023, 4, 20, 4, 37, 57), LocalDateTime(2023, 4, 20, 4, 41, 58)).inOrder()
        verify(medtronicHistoryData, times(2)).addNewHistory(any())
    }

    private fun readHistory(result: PumpHistoryResult) {
        val task: MedtronicUITask = mock()
        whenever(task.result).thenReturn(result)
        whenever(medtronicUIComm.executeCommand(eq(MedtronicCommandType.GetHistoryData), anyOrNull())).thenReturn(task)
        MedtronicPumpPlugin::class.java.getDeclaredMethod("readPumpHistoryLogic").apply { isAccessible = true }.invoke(plugin)
    }

    /** The dates the reads asked the pump for, in order. */
    private fun targetDates(): List<LocalDateTime> {
        val parameters = argumentCaptor<ArrayList<Any>>()
        verify(medtronicUIComm, times(2)).executeCommand(eq(MedtronicCommandType.GetHistoryData), parameters.capture())
        return parameters.allValues.map { it[1] as LocalDateTime }
    }

    private fun resultWith(atechDateTime: Long) =
        PumpHistoryResult(aapsLogger, null, null).apply { addHistoryEntries(mutableListOf(entry(atechDateTime))) }

    private fun entry(atechDateTime: Long) = PumpHistoryEntry().apply { this.atechDateTime = atechDateTime }

    private fun setPrivateField(name: String, value: Any) {
        MedtronicPumpPlugin::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
    }

    companion object {

        /** The newest entry of the read before, at 05:12:57. */
        private const val LAST_ENTRY = 20230420051257L
    }
}
