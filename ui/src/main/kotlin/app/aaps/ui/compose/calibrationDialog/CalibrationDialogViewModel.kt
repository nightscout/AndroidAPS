package app.aaps.ui.compose.calibrationDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ui.confirmationLines
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
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
import app.aaps.core.ui.R as CoreUiR

@HiltViewModel
@Stable
class CalibrationDialogViewModel @Inject constructor(
    private val profileUtil: ProfileUtil,
    @Suppress("unused") private val profileFunction: ProfileFunction,
    private val xDripBroadcast: XDripBroadcast,
    private val xDripSource: XDripSource,
    private val uel: UserEntryLogger,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationDialogUiState())
    val uiState: StateFlow<CalibrationDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data object EntryAccepted : SideEffect()
        data class EntryRejected(val message: String) : SideEffect()
    }

    // replay = 1 so that an EntryAccepted emitted just before screen rotation reaches the
    // recreated collector and the user still navigates back. Snackbar messages may re-show
    // once after rotation; acceptable trade-off to avoid losing navigation events.
    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val units = profileUtil.units
        val currentBg = profileUtil.fromMgdlToUnits(glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0)
        val isMmol = units == GlucoseUnit.MMOL

        _uiState.update {
            CalibrationDialogUiState(
                bg = currentBg,
                units = units,
                bgRange = if (isMmol) 2.0..30.0 else 36.0..500.0,
                bgStep = if (isMmol) 0.1 else 1.0,
                bgDecimalPlaces = if (isMmol) 1 else 0
            )
        }
        refreshPreconditions()
    }

    private fun refreshPreconditions() {
        viewModelScope.launch {
            val result = activePlugin.activeCalibration.checkPreconditions()
            _uiState.update { it.copy(preconditions = result) }
        }
    }

    fun markSensorChangeNow() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val timestamp = dateUtil.now()
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE(
                        timestamp = timestamp,
                        type = TE.Type.SENSOR_CHANGE,
                        glucoseUnit = GlucoseUnit.MGDL
                    ),
                    action = Action.CAREPORTAL,
                    source = Sources.SensorInsert,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.Timestamp(timestamp),
                        ValueWithUnit.TEType(TE.Type.SENSOR_CHANGE)
                    )
                )
                val result = activePlugin.activeCalibration.checkPreconditions()
                _uiState.update { it.copy(preconditions = result) }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    fun updateBg(value: Double) {
        _uiState.update { it.copy(bg = value) }
    }

    fun hasAction(): Boolean = uiState.value.bg > 0.0

    private var confirmedState: CalibrationDialogUiState? = null

    fun buildConfirmationSummary(): List<ConfirmationLine> {
        val state = uiState.value
        confirmedState = state
        val bgText = profileUtil.stringInCurrentUnitsDetect(state.bg)
        val bgWithUnit = rh.gs(CoreUiR.string.value_with_unit, bgText, state.unitLabel)
        return confirmationLines {
            line(ConfirmationRole.PRIMARY, rh.gs(CoreUiR.string.confirmation_line, rh.gs(CoreUiR.string.bg_label), bgWithUnit))
        }
    }

    fun confirmAndSave() {
        if (_uiState.value.submitting) return
        val state = confirmedState ?: return
        if (state.bg <= 0) return
        _uiState.update { it.copy(submitting = true) }
        val bgMgdl = profileUtil.convertToMgdl(state.bg, state.units)
        val timestamp = dateUtil.now()
        viewModelScope.launch {
            try {
                val result = activePlugin.activeCalibration.addEntry(bgMgdl, timestamp)
                val unitValue = ValueWithUnit.fromGlucoseUnit(state.bg, state.units)
                when (result) {
                    AddEntryResult.Accepted    -> {
                        uel.log(action = Action.CALIBRATION, source = Sources.CalibrationDialog, value = unitValue)
                        if (xDripSource.isEnabled()) xDripBroadcast.sendCalibration(state.bg)
                        _sideEffect.emit(SideEffect.EntryAccepted)
                    }

                    is AddEntryResult.Rejected -> {
                        val message = result.message()
                        uel.log(
                            action = Action.CALIBRATION,
                            source = Sources.CalibrationDialog,
                            note = "rejected: $message",
                            value = unitValue
                        )
                        _sideEffect.emit(SideEffect.EntryRejected(message))
                    }
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    fun preconditionMessage(rejected: AddEntryResult.Rejected): String = when (rejected) {
        AddEntryResult.Rejected.NoSession       -> rh.gs(R.string.cal_precheck_no_session)
        is AddEntryResult.Rejected.InWarmUp     -> rh.gs(R.string.cal_precheck_warmup, dateUtil.timeString(rejected.warmUpEndsAt))
        is AddEntryResult.Rejected.DeltaTooHigh -> rh.gs(
            R.string.cal_precheck_delta_too_high,
            formatDeltaInDisplayUnit(rejected.deltaMgdlPer5Min),
            formatDeltaInDisplayUnit(rejected.thresholdMgdlPer5Min),
            profileUtil.unitLabel
        )

        AddEntryResult.Rejected.NoSensorPair    -> rh.gs(R.string.cal_precheck_no_pair)
    }

    private fun AddEntryResult.Rejected.message(): String = when (this) {
        is AddEntryResult.Rejected.DeltaTooHigh -> rh.gs(
            R.string.cal_reject_delta_too_high,
            formatDeltaInDisplayUnit(deltaMgdlPer5Min),
            formatDeltaInDisplayUnit(thresholdMgdlPer5Min),
            profileUtil.unitLabel
        )

        AddEntryResult.Rejected.NoSensorPair    -> rh.gs(R.string.cal_reject_no_pair)
        is AddEntryResult.Rejected.InWarmUp     -> rh.gs(R.string.cal_reject_warmup)
        AddEntryResult.Rejected.NoSession       -> rh.gs(R.string.cal_reject_no_session)
    }

    // Input is already in mg/dL per 5 min — just convert to the user's display unit.
    private fun formatDeltaInDisplayUnit(mgdlPer5Min: Double): String {
        val displayDelta = profileUtil.fromMgdlToUnits(mgdlPer5Min)
        return if (profileUtil.units == GlucoseUnit.MMOL) "%.1f".format(displayDelta) else "%.0f".format(displayDelta)
    }
}
