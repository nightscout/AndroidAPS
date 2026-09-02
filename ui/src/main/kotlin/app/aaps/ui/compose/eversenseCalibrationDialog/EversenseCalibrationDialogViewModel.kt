package app.aaps.ui.compose.eversenseCalibrationDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.EversenseCalibrationSource
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class EversenseCalibrationDialogViewModel @Inject constructor(
    private val profileUtil: ProfileUtil,
    private val eversenseCalibrationSource: EversenseCalibrationSource,
    private val uel: UserEntryLogger,
    private val rh: ResourceHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(EversenseCalibrationDialogUiState())
    val uiState: StateFlow<EversenseCalibrationDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data object CalibrationAccepted : SideEffect()
        data class CalibrationFailed(val message: String) : SideEffect()
    }

    // replay = 1 so a side effect emitted just before screen rotation still reaches the recreated
    // collector — same reasoning as CalibrationDialogViewModel.
    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val units = profileUtil.units
        val isMmol = units == GlucoseUnit.MMOL
        _uiState.update {
            EversenseCalibrationDialogUiState(
                units = units,
                // Eversense's accepted fingerstick calibration range is 40-400 mg/dL.
                bgRange = if (isMmol) 2.2..22.2 else 40.0..400.0,
                bgStep = if (isMmol) 0.1 else 1.0,
                bgDecimalPlaces = if (isMmol) 1 else 0,
                notConnected = !eversenseCalibrationSource.isConnected()
            )
        }
    }

    fun updateBg(value: Double) {
        _uiState.update { it.copy(bg = value) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(submitting = true) }
        val bgMgdl = profileUtil.convertToMgdl(state.bg, state.units).toInt()
        viewModelScope.launch {
            try {
                val success = eversenseCalibrationSource.calibrate(bgMgdl)
                if (success) {
                    uel.log(
                        action = Action.CALIBRATION,
                        source = Sources.Eversense,
                        value = ValueWithUnit.fromGlucoseUnit(state.bg, state.units)
                    )
                    _sideEffect.emit(SideEffect.CalibrationAccepted)
                } else {
                    _sideEffect.emit(SideEffect.CalibrationFailed(rh.gs(R.string.eversense_calibration_send_failed)))
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }
}
