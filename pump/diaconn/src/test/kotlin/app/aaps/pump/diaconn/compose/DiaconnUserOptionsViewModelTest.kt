package app.aaps.pump.diaconn.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnIntKey
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

/**
 * Unit test for the synchronous state logic of [DiaconnUserOptionsViewModel].
 *
 * The `init` block calls `loadFromPump()` directly (not inside a `viewModelScope.launch`), so after
 * construction `uiState.value` reflects the stubbed pump getters plus `preferences.get(BolusSpeed)`.
 * The `update*` methods are pure synchronous `MutableStateFlow.update {}` transforms — the
 * deterministic surface this test covers. The save paths (which use `viewModelScope.launch` /
 * command-queue) are intentionally not exercised here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiaconnUserOptionsViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var preferences: Preferences

    // Concrete final pump class — mockito-kotlin mock() (Mockito 5 inline) constructs it via Objenesis
    // so no real init runs; its Int getters are read synchronously by loadFromPump().
    private val diaconnG8Pump: DiaconnG8Pump = mock()

    private lateinit var sut: DiaconnUserOptionsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())

        // Stub the values loadFromPump() reads during construction so the initial state is deterministic.
        whenever(diaconnG8Pump.beepAndAlarm).thenReturn(2)
        whenever(diaconnG8Pump.alarmIntensity).thenReturn(3)
        whenever(diaconnG8Pump.lcdOnTimeSec).thenReturn(2)
        whenever(diaconnG8Pump.selectedLanguage).thenReturn(2)
        whenever(preferences.get(DiaconnIntKey.BolusSpeed)).thenReturn(5)

        sut = DiaconnUserOptionsViewModel(aapsLogger, rh, commandQueue, diaconnG8Pump, preferences)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoadedFromPumpAndPreferences() {
        val state = sut.uiState.value

        assertThat(state.beepAndAlarm).isEqualTo(2)
        assertThat(state.alarmIntensity).isEqualTo(3)
        assertThat(state.lcdOnTimeSec).isEqualTo(2)
        assertThat(state.selectedLanguage).isEqualTo(2)
        assertThat(state.bolusSpeed).isEqualTo(5)
    }

    @Test
    fun updateBeepAndAlarm_changesOnlyThatField() {
        sut.updateBeepAndAlarm(3)

        val state = sut.uiState.value
        assertThat(state.beepAndAlarm).isEqualTo(3)
        assertThat(state.alarmIntensity).isEqualTo(3)
        assertThat(state.lcdOnTimeSec).isEqualTo(2)
        assertThat(state.selectedLanguage).isEqualTo(2)
        assertThat(state.bolusSpeed).isEqualTo(5)
    }

    @Test
    fun updateAlarmIntensity_changesOnlyThatField() {
        sut.updateAlarmIntensity(1)

        val state = sut.uiState.value
        assertThat(state.alarmIntensity).isEqualTo(1)
        assertThat(state.beepAndAlarm).isEqualTo(2)
    }

    @Test
    fun updateLcdOnTimeSec_changesOnlyThatField() {
        sut.updateLcdOnTimeSec(3)

        val state = sut.uiState.value
        assertThat(state.lcdOnTimeSec).isEqualTo(3)
        assertThat(state.selectedLanguage).isEqualTo(2)
    }

    @Test
    fun updateSelectedLanguage_changesOnlyThatField() {
        sut.updateSelectedLanguage(1)

        val state = sut.uiState.value
        assertThat(state.selectedLanguage).isEqualTo(1)
        assertThat(state.lcdOnTimeSec).isEqualTo(2)
    }

    @Test
    fun updateBolusSpeed_changesOnlyThatField() {
        sut.updateBolusSpeed(8)

        val state = sut.uiState.value
        assertThat(state.bolusSpeed).isEqualTo(8)
        assertThat(state.beepAndAlarm).isEqualTo(2)
    }
}
