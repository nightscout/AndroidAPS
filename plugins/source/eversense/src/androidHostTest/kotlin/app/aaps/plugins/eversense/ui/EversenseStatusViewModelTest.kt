package app.aaps.plugins.eversense.ui

import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.NameTextResolver
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversenseModel
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
 * The banner and the rows of the status tab. Texts are their resource names with the arguments in brackets, so the test
 * sees which string and which value each row uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EversenseStatusViewModelTest {

    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var state = EversenseState()
    private var now = 0L
    private val driver: EversenseDriver = mock<EversenseDriver>().also {
        whenever(it.connectionState).thenReturn(connection)
        whenever(it.getCurrentState()).thenAnswer { state }
        whenever(it.isConnected()).thenAnswer { connection.value == ConnectionState.READY }
        whenever(it.transmitterIs365()).thenReturn(true)
        whenever(it.sensorLifetimeDays()).thenReturn(365)
    }
    private val attention = MutableStateFlow<CgmAttention?>(null)
    private val account = MutableStateFlow<EversenseAccount?>(null)
    private val pairing: EversensePairing = mock<EversensePairing>().also {
        whenever(it.attention).thenReturn(attention)
        whenever(it.account).thenReturn(account)
        whenever(it.transmitter).thenReturn(MutableStateFlow<String?>("D313640"))
    }
    private val dateUtil: DateUtil = mock<DateUtil>().also {
        whenever(it.dateAndTimeString(any())).thenAnswer { call -> "time ${call.getArgument<Long>(0)}" }
        whenever(it.dateString(any())).thenAnswer { call -> "day ${call.getArgument<Long>(0)}" }
        whenever(it.now()).thenAnswer { now }
    }

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = EversenseStatusViewModel(driver, pairing, NameTextResolver(), dateUtil)
    private fun EversenseStatusViewModel.rows(): List<String> = uiState.value.rows.map { "${it.label}=${it.value}" }
    private fun EversenseStatusViewModel.row(label: String): PumpInfoRow = uiState.value.rows.single { it.label == label }

    /** Before the first sync the stored values are defaults, not what the transmitter said: they show as a dash. */
    @Test fun beforeTheFirstSyncBatterySignalAndPhaseAreNotKnown() {
        assertThat(viewModel().rows()).containsExactly(
            "eversense_transmitter=D313640",
            "eversense_pair_account_title=eversense_not_signed_in_no_cloud",
            "eversense_last_sync=eversense_status_never",
            "battery_label=–",
            "eversense_signal=–",
            "eversense_sensor_inserted=eversense_status_never",
            "eversense_calibration_phase=–",
            "eversense_last_calibration=eversense_status_never",
            "eversense_next_calibration=eversense_status_never"
        ).inOrder()
    }

    @Test fun afterASyncTheRowsShowWhatTheTransmitterSaid() {
        connection.value = ConnectionState.READY
        account.value = EversenseAccount("user@example.com", EversenseModel.E365_OTHER)
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
        assertThat(viewModel().rows()).containsExactly(
            "eversense_transmitter=D313640",
            "eversense_pair_account_title=user@example.com",
            "eversense_last_sync=time 1000",
            "battery_label=units_format_percent(85)",
            "eversense_signal=eversense_signal_value(eversense_signal_good,units_format_percent(60))",
            "eversense_sensor_inserted=time 2000",
            "eversense_sensor_end=day ${2_000L + 365 * DAY}",
            "eversense_calibration_phase=eversense_phase_daily",
            "eversense_last_calibration=time 3000",
            "eversense_next_calibration=time 4000",
            "firmware=1.2.3",
            "alarm=${NameTextResolver.name(EversenseAlarm.LOW_GLUCOSE.title)}"
        ).inOrder()
    }

    /** The banner follows the link, in words every language has, and no emoji. */
    @Test fun theBannerFollowsTheLink() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.banner?.text).isEqualTo("eversense_not_connected")
        assertThat(viewModel.uiState.value.banner?.level).isEqualTo(StatusLevel.WARNING)
        assertThat(viewModel.uiState.value.connected).isFalse()
        connection.value = ConnectionState.CONNECTING
        assertThat(viewModel.uiState.value.banner?.text).isEqualTo("connecting")
        connection.value = ConnectionState.READY
        assertThat(viewModel.uiState.value.banner?.text).isEqualTo("eversense_connected")
        assertThat(viewModel.uiState.value.banner?.level).isEqualTo(StatusLevel.NORMAL)
        assertThat(viewModel.uiState.value.connected).isTrue()
        // Nothing outside the basic plane: no emoji or symbol in place of a word
        assertThat(viewModel.rows().joinToString("").any { Character.isSurrogate(it) }).isFalse()
    }

    /** What needs the user comes before the link: no pairing shows no transmitter values at all. */
    @Test fun notPairedAndARefusedLoginTakeTheBanner() {
        connection.value = ConnectionState.READY
        val viewModel = viewModel()
        attention.value = CgmAttention.NOT_PAIRED
        assertThat(viewModel.uiState.value.banner?.text).isEqualTo("eversense_not_paired")
        assertThat(viewModel.uiState.value.rows).isEmpty()
        attention.value = CgmAttention.SIGN_IN_FAILED
        assertThat(viewModel.uiState.value.banner?.text).isEqualTo("eversense_sign_in_failed")
        assertThat(viewModel.uiState.value.banner?.level).isEqualTo(StatusLevel.CRITICAL)
        assertThat(viewModel.uiState.value.rows).isNotEmpty()
    }

    /** An E3 sensor lasts 180 days, a 365 sensor 365. */
    @Test fun theSensorEndFollowsTheModel() {
        whenever(driver.sensorLifetimeDays()).thenReturn(180)
        state = EversenseState().apply { insertionDate = 2_000L }
        assertThat(viewModel().rows()).contains("eversense_sensor_end=day ${2_000L + 180 * DAY}")
    }

    /** Before the model is known there is no lifetime, so no end date either. */
    @Test fun noSensorEndBeforeTheModelIsKnown() {
        whenever(driver.sensorLifetimeDays()).thenReturn(null)
        state = EversenseState().apply { insertionDate = 2_000L }
        assertThat(viewModel().uiState.value.rows.map { it.label }).doesNotContain("eversense_sensor_end")
    }

    /** The rows that need a look are coloured: a low battery, a weak signal, a due calibration, an ended sensor. */
    @Test fun rowsThatNeedALookAreColoured() {
        now = 1_000L * DAY
        state = EversenseState().apply {
            lastSync = now
            batteryPercentage = 8
            sensorSignalStrength = 28
            insertionDate = now - 366 * DAY
            nextCalibrationDate = now - 1
            activeAlarms = listOf(ActiveAlarm(EversenseAlarm.LOW_GLUCOSE, EversenseAlarm.LOW_GLUCOSE.code, 0, 0))
        }
        val viewModel = viewModel()
        assertThat(viewModel.row("battery_label").level).isEqualTo(StatusLevel.CRITICAL)
        assertThat(viewModel.row("eversense_signal").level).isEqualTo(StatusLevel.CRITICAL)
        assertThat(viewModel.row("eversense_next_calibration").level).isEqualTo(StatusLevel.WARNING)
        assertThat(viewModel.row("eversense_sensor_end").level).isEqualTo(StatusLevel.CRITICAL)
        assertThat(viewModel.row("alarm").level).isEqualTo(StatusLevel.CRITICAL)
    }

    /** The overview's rule: a 365 sensor turns orange in its last 30 days, and red once it has ended. */
    @Test fun aSensorInItsLastMonthIsAWarning() {
        now = 1_000L * DAY
        state = EversenseState().apply { insertionDate = now - 340 * DAY }
        assertThat(viewModel().row("eversense_sensor_end").level).isEqualTo(StatusLevel.WARNING)
    }

    @Test fun rowsThatAreFineAreNotColoured() {
        now = 1_000L * DAY
        state = EversenseState().apply {
            lastSync = now
            batteryPercentage = 85
            sensorSignalStrength = 60
            insertionDate = now - 10 * DAY
            nextCalibrationDate = now + DAY
        }
        val viewModel = viewModel()
        assertThat(viewModel.row("battery_label").level).isEqualTo(StatusLevel.UNSPECIFIED)
        assertThat(viewModel.row("eversense_signal").level).isEqualTo(StatusLevel.NORMAL)
        assertThat(viewModel.row("eversense_next_calibration").level).isEqualTo(StatusLevel.UNSPECIFIED)
        assertThat(viewModel.row("eversense_sensor_end").level).isEqualTo(StatusLevel.UNSPECIFIED)
    }

    private companion object {

        const val DAY = 24 * 60 * 60 * 1000L
    }
}
