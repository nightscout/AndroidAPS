package info.nightscout.pump.combov2

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth.assertThat
import info.nightscout.comboctl.main.Pump
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoMoreInteractions
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A multiwave bolus from the pump history reaches IOB (#5379). It used to be dropped, so insulin given
 * as a multiwave on the pump was missing from IOB, and the loop dosed on top of it.
 */
@OptIn(ExperimentalTime::class)
class ComboV2PluginMultiwaveTest {

    private val pumpSync: PumpSync = mock()
    private lateinit var plugin: ComboV2Plugin

    private val start = Instant.fromEpochMilliseconds(1_000_000L)

    @BeforeEach
    fun setUp() {
        // Not paired in a test, so the serial number is this text
        val rh: ResourceHelper = mock { on { gs(any<Int>()) } doReturn "serial" }
        plugin = ComboV2Plugin(
            aapsLogger = mock(),
            rh = rh,
            preferences = mock(),
            commandQueue = mock(),
            context = mock(),
            rxBus = mock(),
            sp = mock(),
            pumpSync = pumpSync,
            dateUtil = mock(),
            notificationManager = mock(),
            config = mock(),
            pumpEnactResultProvider = { mock() },
            bolusProgressData = mock(),
            concentrationHelper = mock()
        )
    }

    @Test
    fun theImmediatePartIsABolusAndTheRestAnExtendedBolus() {
        // 3.0 U in all, 1.0 U of it now and 2.0 U over an hour. In 0.1 U units.
        plugin.handlePumpEvent(
            Pump.Event.MultiwaveBolusStarted(bolusId = 7, timestamp = start, totalBolusAmount = 30, immediateBolusAmount = 10, totalDurationMinutes = 60)
        )

        val bolus = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(1_000_000L), bolus.capture(), eq(BS.Type.NORMAL), eq(7L), eq(PumpType.ACCU_CHEK_COMBO), eq("serial")) }
        assertThat(bolus.firstValue.cU).isEqualTo(1.0)

        val extended = argumentCaptor<PumpRate>()
        verifyBlocking(pumpSync) {
            syncExtendedBolusWithPumpId(eq(1_000_000L), extended.capture(), eq(60 * 60_000L), eq(false), eq(7L), eq(PumpType.ACCU_CHEK_COMBO), eq("serial"))
        }
        // The extended bolus holds the rest only: the total includes the immediate part
        assertThat(extended.firstValue.cU).isEqualTo(2.0)
    }

    @Test
    fun aMultiwaveWithNothingLeftForLaterIsOnlyABolus() {
        plugin.handlePumpEvent(
            Pump.Event.MultiwaveBolusStarted(bolusId = 8, timestamp = start, totalBolusAmount = 10, immediateBolusAmount = 10, totalDurationMinutes = 30)
        )

        val bolus = argumentCaptor<PumpInsulin>()
        verifyBlocking(pumpSync) { syncBolusWithPumpId(eq(1_000_000L), bolus.capture(), eq(BS.Type.NORMAL), eq(8L), eq(PumpType.ACCU_CHEK_COMBO), eq("serial")) }
        verifyNoMoreInteractions(pumpSync)
    }

    @Test
    fun theEndOfAMultiwaveEndsItsExtendedBolus() {
        val end = Instant.fromEpochMilliseconds(2_000_000L)

        plugin.handlePumpEvent(
            Pump.Event.MultiwaveBolusEnded(bolusId = 9, timestamp = end, totalBolusAmount = 30, immediateBolusAmount = 10, totalDurationMinutes = 60)
        )

        verifyBlocking(pumpSync) { syncStopExtendedBolusWithPumpId(eq(2_000_000L), eq(9L), eq(PumpType.ACCU_CHEK_COMBO), eq("serial")) }
        verifyNoMoreInteractions(pumpSync)
    }
}
