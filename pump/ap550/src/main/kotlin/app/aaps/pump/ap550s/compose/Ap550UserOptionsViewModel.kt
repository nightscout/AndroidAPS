package app.aaps.pump.ap550s.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.ap550s.AP550Pump
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Values of the pump's own user menu, as shown on the pump display.
 *
 * [alarm] is one of `ALARM_SOUND`, `ALARM_VIBRATE` or `ALARM_BOTH`. [beepEnabled] is a separate
 * switch on the pump, so it is kept apart from the alarm choice even though both live in the same
 * `beepAndAlarm` byte on the pump.
 */
@Immutable
data class Ap550UserOptionsUiState(
    val timeDisplayType24: Boolean = false,
    val buttonScrollOnOff: Boolean = false,
    val beepEnabled: Boolean = false,
    val alarm: Int = Ap550UserOptionsViewModel.ALARM_SOUND,
    val lcdOnTimeSec: Int = 5,
    val backlightOnTimeSec: Int = 0,
    val mmol: Boolean = true,
    val shutdownHour: Int = 0,
    val lowReservoirRate: Int = 10,
    val isSaving: Boolean = false
)

sealed class Ap550UserOptionsEvent {
    data object Saved : Ap550UserOptionsEvent()
    data class Error(val message: String) : Ap550UserOptionsEvent()
}

/**
 * Pump user options screen.
 *
 * Every value is read from the pump object when the screen opens and written back to it on save.
 * The save itself is a command queue round trip, so it can fail when the pump is out of range.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class Ap550UserOptionsViewModel(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue,
    private val ap550Pump: AP550Pump
) : ViewModel() {

    private val _uiState = MutableStateFlow(Ap550UserOptionsUiState())
    val uiState: StateFlow<Ap550UserOptionsUiState> = _uiState

    private val _events = MutableSharedFlow<Ap550UserOptionsEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<Ap550UserOptionsEvent> = _events

    /** Lowest backlight time the hardware can show. Older models cannot switch the backlight off. */
    val minBacklightOnTimeSec: Int get() = if (ap550Pump.hwModel >= MIN_HW_MODEL_FOR_ZERO_BACKLIGHT) 0 else 1

    init {
        loadFromPump()
    }

    private fun loadFromPump() {
        aapsLogger.debug(
            LTag.PUMP,
            "UserOptionsLoaded: timeDisplayType24=${ap550Pump.timeDisplayType24}" +
                " buttonScrollOnOff=${ap550Pump.buttonScrollOnOff}" +
                " beepAndAlarm=${ap550Pump.beepAndAlarm}" +
                " lcdOnTimeSec=${ap550Pump.lcdOnTimeSec}" +
                " backlightOnTimeSec=${ap550Pump.backlightOnTimeSec}" +
                " units=${ap550Pump.units}" +
                " shutdownHour=${ap550Pump.shutdownHour}" +
                " lowReservoirRate=${ap550Pump.lowReservoirRate}"
        )
        _uiState.value = Ap550UserOptionsUiState(
            timeDisplayType24 = ap550Pump.timeDisplayType24,
            buttonScrollOnOff = ap550Pump.buttonScrollOnOff,
            beepEnabled = ap550Pump.beepAndAlarm and BEEP_MASK != 0,
            alarm = alarmFrom(ap550Pump.beepAndAlarm),
            lcdOnTimeSec = ap550Pump.lcdOnTimeSec,
            backlightOnTimeSec = ap550Pump.backlightOnTimeSec,
            mmol = ap550Pump.units == AP550Pump.UNITS_MMOL,
            shutdownHour = ap550Pump.shutdownHour,
            lowReservoirRate = ap550Pump.lowReservoirRate
        )
    }

    fun updateTimeDisplayType24(value: Boolean) = _uiState.update { it.copy(timeDisplayType24 = value) }
    fun updateButtonScrollOnOff(value: Boolean) = _uiState.update { it.copy(buttonScrollOnOff = value) }
    fun updateBeepEnabled(value: Boolean) = _uiState.update { it.copy(beepEnabled = value) }
    fun updateAlarm(value: Int) = _uiState.update { it.copy(alarm = value) }
    fun updateLcdOnTimeSec(value: Int) = _uiState.update { it.copy(lcdOnTimeSec = value) }
    fun updateBacklightOnTimeSec(value: Int) = _uiState.update { it.copy(backlightOnTimeSec = value) }
    fun updateMmol(value: Boolean) = _uiState.update { it.copy(mmol = value) }
    fun updateShutdownHour(value: Int) = _uiState.update { it.copy(shutdownHour = value) }
    fun updateLowReservoirRate(value: Int) = _uiState.update { it.copy(lowReservoirRate = value) }

    fun save() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }

        ap550Pump.timeDisplayType24 = state.timeDisplayType24
        ap550Pump.buttonScrollOnOff = state.buttonScrollOnOff
        ap550Pump.beepAndAlarm = (state.alarm and ALARM_MASK) or if (state.beepEnabled) BEEP_MASK else 0
        ap550Pump.lcdOnTimeSec = state.lcdOnTimeSec
        ap550Pump.backlightOnTimeSec = state.backlightOnTimeSec
        ap550Pump.units = if (state.mmol) AP550Pump.UNITS_MMOL else AP550Pump.UNITS_MGDL
        ap550Pump.shutdownHour = state.shutdownHour
        ap550Pump.lowReservoirRate = state.lowReservoirRate

        viewModelScope.launch {
            val result = commandQueue.setUserOptions()
            _uiState.update { it.copy(isSaving = false) }
            if (result.success) _events.tryEmit(Ap550UserOptionsEvent.Saved)
            else _events.tryEmit(Ap550UserOptionsEvent.Error(result.comment))
        }
    }

    companion object {

        const val ALARM_SOUND = 1
        const val ALARM_VIBRATE = 2
        const val ALARM_BOTH = 3

        /** Alarm choice occupies the two low bits of the pump's `beepAndAlarm` byte. */
        private const val ALARM_MASK = 0b011

        /** Button beep is the third bit of the same byte. */
        private const val BEEP_MASK = 0b100

        /** Hardware from this model on can switch the backlight fully off. */
        private const val MIN_HW_MODEL_FOR_ZERO_BACKLIGHT = 7

        private fun alarmFrom(beepAndAlarm: Int): Int = when (beepAndAlarm and ALARM_MASK) {
            ALARM_VIBRATE -> ALARM_VIBRATE
            ALARM_BOTH    -> ALARM_BOTH
            else          -> ALARM_SOUND
        }
    }
}