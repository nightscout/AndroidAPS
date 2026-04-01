package app.aaps.pump.dana.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

data class UserOptionsUiState(
    val timeFormat24h: Boolean = false,
    val buttonScroll: Boolean = false,
    val beepOnPress: Boolean = false,
    val alarmMode: Int = 1, // 1=Sound, 2=Vibrate, 3=Both
    val screenTimeout: Int = 5,
    val backlight: Int = 1,
    val glucoseUnitMmol: Boolean = false,
    val shutdownHour: Int = 0,
    val lowReservoir: Int = 10,
    val minBacklight: Int = 1
)

sealed class UserOptionsEvent {
    data object Saved : UserOptionsEvent()
    data class Error(val message: String) : UserOptionsEvent()
}

@HiltViewModel
@Stable
class DanaUserOptionsViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val danaPump: DanaPump,
    private val commandQueue: CommandQueue,
    private val uiInteraction: UiInteraction
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserOptionsUiState())
    val uiState: StateFlow<UserOptionsUiState> = _uiState

    private val _events = MutableSharedFlow<UserOptionsEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<UserOptionsEvent> = _events

    init {
        loadFromPump()
    }

    fun loadFromPump() {
        val minBl = if (danaPump.hwModel < 7) 1 else 0
        _uiState.value = UserOptionsUiState(
            timeFormat24h = danaPump.timeDisplayType24,
            buttonScroll = danaPump.buttonScrollOnOff,
            beepOnPress = danaPump.beepAndAlarm > 4,
            alarmMode = (danaPump.beepAndAlarm and 0x03).let { if (it == 0) 1 else it },
            screenTimeout = danaPump.lcdOnTimeSec,
            backlight = danaPump.backlightOnTimeSec,
            glucoseUnitMmol = danaPump.getUnits() == GlucoseUnit.MMOL.asText,
            shutdownHour = danaPump.shutdownHour,
            lowReservoir = danaPump.lowReservoirRate,
            minBacklight = minBl
        )

        aapsLogger.debug(
            LTag.PUMP, "UserOptions loaded: timeFormat24=${danaPump.timeDisplayType24} " +
                "beepAndAlarm=${danaPump.beepAndAlarm} lcdOnTime=${danaPump.lcdOnTimeSec} " +
                "backlight=${danaPump.backlightOnTimeSec} lowReservoir=${danaPump.lowReservoirRate}"
        )
    }

    fun updateTimeFormat(value: Boolean) = _uiState.update { it.copy(timeFormat24h = value) }
    fun updateButtonScroll(value: Boolean) = _uiState.update { it.copy(buttonScroll = value) }
    fun updateBeepOnPress(value: Boolean) = _uiState.update { it.copy(beepOnPress = value) }
    fun updateAlarmMode(value: Int) = _uiState.update { it.copy(alarmMode = value) }
    fun updateScreenTimeout(value: Double) = _uiState.update { it.copy(screenTimeout = value.toInt()) }
    fun updateBacklight(value: Double) = _uiState.update { it.copy(backlight = value.toInt()) }
    fun updateGlucoseUnit(value: Boolean) = _uiState.update { it.copy(glucoseUnitMmol = value) }
    fun updateShutdownHour(value: Double) = _uiState.update { it.copy(shutdownHour = value.toInt()) }
    fun updateLowReservoir(value: Double) = _uiState.update { it.copy(lowReservoir = value.toInt()) }

    fun save() {
        val state = _uiState.value

        danaPump.timeDisplayType24 = state.timeFormat24h
        danaPump.buttonScrollOnOff = state.buttonScroll

        var beepAlarm = state.alarmMode
        if (state.beepOnPress) beepAlarm += 4
        danaPump.beepAndAlarm = beepAlarm

        danaPump.lcdOnTimeSec = min(max(state.screenTimeout / 5 * 5, 5), 240)
        danaPump.backlightOnTimeSec = min(max(state.backlight, state.minBacklight), 60)
        danaPump.units = if (state.glucoseUnitMmol) 1 else 0
        danaPump.shutdownHour = min(state.shutdownHour, 24)
        danaPump.lowReservoirRate = min(max(state.lowReservoir / 10 * 10, 10), 50)

        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (!result.success) {
                    _events.tryEmit(UserOptionsEvent.Error(result.comment))
                } else {
                    _events.tryEmit(UserOptionsEvent.Saved)
                }
            }
        })
    }
}
