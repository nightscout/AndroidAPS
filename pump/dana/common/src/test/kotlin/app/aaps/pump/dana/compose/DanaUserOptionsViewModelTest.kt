package app.aaps.pump.dana.compose

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class DanaUserOptionsViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var uiInteraction: UiInteraction

    private val danaPump: DanaPump = mock()

    private lateinit var sut: DanaUserOptionsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // save() uses viewModelScope; loadFromPump() in init runs synchronously against the pump getters
        // (all Int/Boolean defaults on a mock; unitsString returns null which the null-safe compare tolerates).
        Dispatchers.setMain(StandardTestDispatcher())
        sut = DanaUserOptionsViewModel(aapsLogger, rh, danaPump, commandQueue, uiInteraction)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loadFromPump maps default pump state`() {
        val state = sut.uiState.value
        // hwModel 0 (< 7) -> minBacklight 1; beepAndAlarm 0 -> alarmMode 1, beepOnPress false; units null -> mgdl.
        assertThat(state.minBacklight).isEqualTo(1)
        assertThat(state.alarmMode).isEqualTo(1)
        assertThat(state.beepOnPress).isFalse()
        assertThat(state.timeFormat24h).isFalse()
        assertThat(state.glucoseUnitMmol).isFalse()
    }

    @Test
    fun `loadFromPump maps configured pump fields including mmol units`() {
        whenever(danaPump.hwModel).thenReturn(7)               // >= 7 -> minBacklight 0
        whenever(danaPump.timeDisplayType24).thenReturn(true)
        whenever(danaPump.beepAndAlarm).thenReturn(6)          // > 4 -> beepOnPress true; 6 and 3 = 2 -> alarmMode 2
        whenever(danaPump.lcdOnTimeSec).thenReturn(30)
        whenever(danaPump.backlightOnTimeSec).thenReturn(20)
        whenever(danaPump.shutdownHour).thenReturn(5)
        whenever(danaPump.lowReservoirRate).thenReturn(20)
        // This stub is the whole point of the fix: `unitsString` no longer collides with the `units` getter.
        whenever(danaPump.unitsString).thenReturn(GlucoseUnit.MMOL.asText)

        sut.loadFromPump()

        val state = sut.uiState.value
        assertThat(state.minBacklight).isEqualTo(0)
        assertThat(state.timeFormat24h).isTrue()
        assertThat(state.beepOnPress).isTrue()
        assertThat(state.alarmMode).isEqualTo(2)
        assertThat(state.screenTimeout).isEqualTo(30)
        assertThat(state.backlight).isEqualTo(20)
        assertThat(state.shutdownHour).isEqualTo(5)
        assertThat(state.lowReservoir).isEqualTo(20)
        assertThat(state.glucoseUnitMmol).isTrue()
    }

    @Test
    fun `editor setters update the state`() {
        sut.updateTimeFormat(true)
        sut.updateButtonScroll(true)
        sut.updateBeepOnPress(true)
        sut.updateAlarmMode(3)
        sut.updateGlucoseUnit(true)
        sut.updateScreenTimeout(45.0)
        sut.updateBacklight(12.0)
        sut.updateShutdownHour(18.0)
        sut.updateLowReservoir(30.0)

        val state = sut.uiState.value
        assertThat(state.timeFormat24h).isTrue()
        assertThat(state.buttonScroll).isTrue()
        assertThat(state.beepOnPress).isTrue()
        assertThat(state.alarmMode).isEqualTo(3)
        assertThat(state.glucoseUnitMmol).isTrue()
        assertThat(state.screenTimeout).isEqualTo(45)
        assertThat(state.backlight).isEqualTo(12)
        assertThat(state.shutdownHour).isEqualTo(18)
        assertThat(state.lowReservoir).isEqualTo(30)
    }
}
