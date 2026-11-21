package app.aaps.plugins.eversense.ui

import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.NameTextResolver
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversensePairing
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The lines of the status tab. Texts are their resource names with the arguments in brackets, so the test sees which
 * string and which value each line uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EversenseStatusViewModelTest {

    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var state = EversenseState()
    private val driver: EversenseDriver = mock<EversenseDriver>().also {
        whenever(it.connectionState).thenReturn(connection)
        whenever(it.getCurrentState()).thenAnswer { state }
        whenever(it.isConnected()).thenAnswer { connection.value == ConnectionState.READY }
        whenever(it.transmitterIs365()).thenReturn(true)
    }
    private val pairing: EversensePairing = mock<EversensePairing>().also {
        whenever(it.attention).thenReturn(MutableStateFlow<CgmAttention?>(null))
        whenever(it.account).thenReturn(MutableStateFlow<EversenseAccount?>(null))
        whenever(it.transmitter).thenReturn(MutableStateFlow<String?>("T123456"))
    }
    private val dateUtil: DateUtil = mock<DateUtil>().also {
        whenever(it.dateAndTimeString(any())).thenAnswer { call -> "time ${call.getArgument<Long>(0)}" }
        whenever(it.dateString(any())).thenAnswer { call -> "day ${call.getArgument<Long>(0)}" }
    }

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun lines(): List<String> = EversenseStatusViewModel(driver, pairing, NameTextResolver(), dateUtil).uiState.value.lines

    /** Before the first sync the stored values are defaults, not what the transmitter said: they show as a dash. */
    @Test fun beforeTheFirstSyncBatterySignalAndPhaseAreNotKnown() {
        assertThat(lines()).containsExactly(
            "eversense_status_connected_label(no)",
            "eversense_status_battery(–)",
            "eversense_status_insertion_label(eversense_status_never)",
            "eversense_status_last_sync_label(eversense_status_never)",
            "eversense_status_signal_label(–)",
            "eversense_status_phase_label(–)",
            "eversense_status_last_cal_label(eversense_status_never)",
            "eversense_status_next_cal_label(eversense_status_never)"
        ).inOrder()
    }

    @Test fun afterASyncTheLinesShowWhatTheTransmitterSaid() {
        connection.value = ConnectionState.READY
        state = EversenseState().apply {
            lastSync = 1_000L
            batteryPercentage = 85
            sensorSignalStrength = 60
            calibrationPhase = CalibrationPhase.DAILY_CALIBRATION
            insertionDate = 2_000L
            lastCalibrationDate = 3_000L
            nextCalibrationDate = 4_000L
            firmwareVersion = "1.2.3"
            activeAlarms = listOf(ActiveAlarm(EversenseAlarm.LOW_GLUCOSE, EversenseAlarm.LOW_GLUCOSE.code, 0, 0))
        }
        val lines = lines()
        assertThat(lines).containsExactly(
            "eversense_status_connected_label(yes)",
            "eversense_status_battery(units_format_percent(85))",
            "eversense_status_insertion_label(time 2000)",
            "eversense_status_sensor_end(day ${2_000L + 365 * 24 * 60 * 60 * 1000L})",
            "eversense_status_last_sync_label(time 1000)",
            "eversense_status_signal_label(eversense_signal_value(eversense_signal_good,units_format_percent(60)))",
            "eversense_status_phase_label(eversense_phase_daily)",
            "eversense_status_last_cal_label(time 3000)",
            "eversense_status_next_cal_label(time 4000)",
            "eversense_status_firmware(1.2.3)",
            "eversense_status_alarm(${NameTextResolver.name(EversenseAlarm.LOW_GLUCOSE.title)})"
        ).inOrder()
    }

    /** Connected is the core Yes or No, which every language has, and no emoji. */
    @Test fun connectedFollowsTheLinkAsYesOrNo() {
        val viewModel = EversenseStatusViewModel(driver, pairing, NameTextResolver(), dateUtil)
        assertThat(viewModel.uiState.value.lines.first()).isEqualTo("eversense_status_connected_label(no)")
        assertThat(viewModel.uiState.value.connected).isFalse()
        connection.value = ConnectionState.READY
        assertThat(viewModel.uiState.value.lines.first()).isEqualTo("eversense_status_connected_label(yes)")
        assertThat(viewModel.uiState.value.connected).isTrue()
        // Nothing outside the basic plane: no emoji or symbol in place of a word
        assertThat(viewModel.uiState.value.lines.joinToString("").any { Character.isSurrogate(it) }).isFalse()
    }

    /** An E3 sensor lasts 180 days, a 365 sensor 365. */
    @Test fun theSensorEndFollowsTheModel() {
        whenever(driver.transmitterIs365()).thenReturn(false)
        state = EversenseState().apply { insertionDate = 2_000L }
        assertThat(lines()).contains("eversense_status_sensor_end(day ${2_000L + 180 * 24 * 60 * 60 * 1000L})")
    }
}
