package app.aaps.ui.compose.calibrationDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.XDripBroadcast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
@Stable
class CalibrationDialogViewModel @Inject constructor(
    private val profileUtil: ProfileUtil,
    private val profileFunction: ProfileFunction,
    private val xDripBroadcast: XDripBroadcast,
    private val uel: UserEntryLogger,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    val rh: ResourceHelper
) : ViewModel() {

    val uiState: StateFlow<CalibrationDialogUiState>
        field = MutableStateFlow(CalibrationDialogUiState())

    init {
        val units = profileUtil.units
        val currentBg = profileUtil.fromMgdlToUnits(glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0)
        val isMmol = units == GlucoseUnit.MMOL

        uiState.update {
            CalibrationDialogUiState(
                bg = currentBg,
                units = units,
                bgRange = if (isMmol) 2.0..30.0 else 36.0..500.0,
                bgStep = if (isMmol) 0.1 else 1.0,
                bgDecimalPlaces = if (isMmol) 1 else 0
            )
        }
    }

    fun updateBg(value: Double) {
        uiState.update { it.copy(bg = value) }
    }

    fun hasAction(): Boolean = uiState.value.bg > 0.0

    private var confirmedState: CalibrationDialogUiState? = null

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        confirmedState = state
        val bgText = profileUtil.stringInCurrentUnitsDetect(state.bg)
        return listOf("${rh.gs(app.aaps.core.ui.R.string.bg_label)}: $bgText ${state.unitLabel}")
    }

    fun confirmAndSave() {
        val state = confirmedState ?: return
        if (state.bg > 0) {
            uel.log(
                action = Action.CALIBRATION,
                source = Sources.CalibrationDialog,
                value = ValueWithUnit.fromGlucoseUnit(state.bg, state.units)
            )
            xDripBroadcast.sendCalibration(state.bg)
        }
    }
}
