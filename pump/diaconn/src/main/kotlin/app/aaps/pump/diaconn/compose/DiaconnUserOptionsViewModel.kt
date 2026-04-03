package app.aaps.pump.diaconn.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DiaconnUserOptionsUiState(
    val beepAndAlarm: Int = 1,      // 1=sound, 2=vibrate, 3=silent
    val alarmIntensity: Int = 1,    // 1=low, 2=middle, 3=high
    val lcdOnTimeSec: Int = 1,      // 1=10s, 2=20s, 3=30s
    val selectedLanguage: Int = 3,  // 1=Chinese, 2=Korean, 3=English
    val bolusSpeed: Int = 8         // 1-8
)

sealed class DiaconnUserOptionsEvent {
    data object Saved : DiaconnUserOptionsEvent()
    data class Error(val message: String) : DiaconnUserOptionsEvent()
}

@HiltViewModel
@Stable
class DiaconnUserOptionsViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    private val diaconnG8Pump: DiaconnG8Pump,
    private val preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaconnUserOptionsUiState())
    val uiState: StateFlow<DiaconnUserOptionsUiState> = _uiState

    private val _events = MutableSharedFlow<DiaconnUserOptionsEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<DiaconnUserOptionsEvent> = _events

    init {
        loadFromPump()
    }

    private fun loadFromPump() {
        aapsLogger.debug(
            LTag.PUMP,
            "UserOptionsLoaded: beepAndAlarm=${diaconnG8Pump.beepAndAlarm}" +
                " alarmIntensity=${diaconnG8Pump.alarmIntensity}" +
                " lcdOnTimeSec=${diaconnG8Pump.lcdOnTimeSec}" +
                " selectedLanguage=${diaconnG8Pump.selectedLanguage}"
        )
        _uiState.value = DiaconnUserOptionsUiState(
            beepAndAlarm = diaconnG8Pump.beepAndAlarm,
            alarmIntensity = diaconnG8Pump.alarmIntensity,
            lcdOnTimeSec = diaconnG8Pump.lcdOnTimeSec,
            selectedLanguage = diaconnG8Pump.selectedLanguage,
            bolusSpeed = preferences.get(DiaconnIntKey.BolusSpeed)
        )
    }

    fun updateBeepAndAlarm(value: Int) = _uiState.update { it.copy(beepAndAlarm = value) }
    fun updateAlarmIntensity(value: Int) = _uiState.update { it.copy(alarmIntensity = value) }
    fun updateLcdOnTimeSec(value: Int) = _uiState.update { it.copy(lcdOnTimeSec = value) }
    fun updateSelectedLanguage(value: Int) = _uiState.update { it.copy(selectedLanguage = value) }
    fun updateBolusSpeed(value: Int) = _uiState.update { it.copy(bolusSpeed = value) }

    fun saveAlarm() {
        val state = _uiState.value
        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.ALARM
        diaconnG8Pump.beepAndAlarm = state.beepAndAlarm
        diaconnG8Pump.alarmIntensity = state.alarmIntensity
        saveToCommandQueue()
    }

    fun saveLcdOnTime() {
        val state = _uiState.value
        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.LCD
        diaconnG8Pump.lcdOnTimeSec = state.lcdOnTimeSec
        saveToCommandQueue()
    }

    fun saveLanguage() {
        val state = _uiState.value
        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.LANG
        diaconnG8Pump.selectedLanguage = state.selectedLanguage
        saveToCommandQueue()
    }

    fun saveBolusSpeed() {
        val state = _uiState.value
        diaconnG8Pump.bolusSpeed = state.bolusSpeed
        diaconnG8Pump.speed = state.bolusSpeed
        diaconnG8Pump.setUserOptionType = DiaconnG8Pump.BOLUS_SPEED
        preferences.put(DiaconnIntKey.BolusSpeed, state.bolusSpeed)
        _events.tryEmit(DiaconnUserOptionsEvent.Saved)
    }

    private fun saveToCommandQueue() {
        commandQueue.setUserOptions(object : Callback() {
            override fun run() {
                if (result.success) {
                    _events.tryEmit(DiaconnUserOptionsEvent.Saved)
                } else {
                    _events.tryEmit(DiaconnUserOptionsEvent.Error(result.comment))
                }
            }
        })
    }
}
