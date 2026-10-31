package app.aaps.ui.compose.calibrationDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.isNotDeliveryError
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.failText
import app.aaps.ui.UiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The calibration dialog. A calibration and a sensor change are stored by the MASTER, on its own sensor data, so
 * the BG the loop doses on is calibrated: on a client both go over the round trip (see [BatchExecutor]). The master
 * checks the conditions and writes the confirmation; this screen only shows them.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class CalibrationDialogViewModel(
    private val profileUtil: ProfileUtil,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val batchExecutor: BatchExecutor,
    private val config: Config
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

    /** The master's confirmation of a prepared calibration, waiting for the user's OK. */
    data class Confirmation(val id: Long, val lines: List<ConfirmationLine>)

    private val _confirmation = MutableStateFlow<Confirmation?>(null)
    val confirmation: StateFlow<Confirmation?> = _confirmation.asStateFlow()

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
        // A sensor change or a calibration stored by the master reaches a client through Nightscout a little later.
        merge(persistenceLayer.observeChanges<TE>().map { }, persistenceLayer.observeChanges<CAL>().map { })
            .onEach { refreshPreconditions() }
            .launchIn(viewModelScope)
    }

    private fun refreshPreconditions() {
        viewModelScope.launch {
            val result = activePlugin.activeCalibration.checkPreconditions()
            _uiState.update { it.copy(preconditions = result) }
        }
    }

    /** Store a sensor change now. The button is the user's decision, so there is no confirmation of its own. */
    fun markSensorChangeNow() {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val label = rh.gs(CoreUiStrings.cgm_sensor_insert)
                val action = BatchAction.TherapyEvent(teType = TE.Type.SENSOR_CHANGE, timestamp = dateUtil.now(), source = Sources.SensorInsert)
                when (val prepared = batchExecutor.prepare(listOf(action), Sources.SensorInsert, label)) {
                    is ActionProgress.Prepared -> (batchExecutor.commit(prepared.id, Sources.SensorInsert, label) as? ActionProgress.Rejected)?.let { reject(it) }
                    is ActionProgress.Rejected -> reject(prepared)
                    else                       -> Unit
                }
                refreshPreconditions()
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    fun updateBg(value: Double) {
        _uiState.update { it.copy(bg = value) }
    }

    fun hasAction(): Boolean = uiState.value.bg > 0.0

    /** Ask the master to check the calibration and describe it. Nothing is stored yet. */
    fun requestCalibration() {
        val state = uiState.value
        if (state.submitting || state.bg <= 0) return
        val bgMgdl = profileUtil.convertToMgdl(state.bg, state.units)
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                when (val prepared = batchExecutor.prepare(listOf(BatchAction.Calibration(bgMgdl)), Sources.CalibrationDialog, label())) {
                    is ActionProgress.Prepared -> _confirmation.value = Confirmation(prepared.id, prepared.lines)
                    is ActionProgress.Rejected -> reject(prepared)
                    else                       -> Unit
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    /** The user confirmed the master's description: the master stores the calibration. */
    fun confirmCalibration() {
        val pending = _confirmation.value ?: return
        _confirmation.value = null
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                when (val result = batchExecutor.commit(pending.id, Sources.CalibrationDialog, label())) {
                    ActionProgress.Applied     -> _sideEffect.emit(SideEffect.EntryAccepted)
                    is ActionProgress.Rejected -> reject(result)
                    else                       -> Unit
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    /** The user cancelled. The master drops the prepared calibration on its own after a while. */
    fun dismissCalibration() {
        _confirmation.value = null
    }

    private fun label(): String = rh.gs(CoreUiStrings.calibration)

    /**
     * A refusal: the master's reason (BG changing too fast, no sensor session, ...) or this client's offline block.
     * On a client the round-trip modal already shows a reason that came back from the master. A commit that was
     * already used (a double tap) stays silent.
     */
    private suspend fun reject(rejected: ActionProgress.Rejected) {
        if (rejected.reason == FailureReason.NoPendingBolus) return
        val message = when {
            rejected.reason.isNotDeliveryError() -> rh.gs(rejected.reason.failText())
            config.AAPSCLIENT                    -> return
            else                                 -> rejected.detail ?: rh.gs(rejected.reason.failText())
        }
        _sideEffect.emit(SideEffect.EntryRejected(message))
    }

    fun preconditionMessage(rejected: AddEntryResult.Rejected): String = when (rejected) {
        AddEntryResult.Rejected.NoSession       -> rh.gs(UiStrings.cal_precheck_no_session)
        is AddEntryResult.Rejected.InWarmUp     -> rh.gs(UiStrings.cal_precheck_warmup, dateUtil.timeString(rejected.warmUpEndsAt))
        is AddEntryResult.Rejected.DeltaTooHigh -> rh.gs(
            UiStrings.cal_precheck_delta_too_high,
            formatDeltaInDisplayUnit(rejected.deltaMgdlPer5Min),
            formatDeltaInDisplayUnit(rejected.thresholdMgdlPer5Min),
            profileUtil.unitLabel
        )

        AddEntryResult.Rejected.NoSensorPair    -> rh.gs(UiStrings.cal_precheck_no_pair)
    }

    // Input is already in mg/dL per 5 min — just convert to the user's display unit.
    private fun formatDeltaInDisplayUnit(mgdlPer5Min: Double): String {
        val displayDelta = profileUtil.fromMgdlToUnits(mgdlPer5Min)
        return if (profileUtil.units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(displayDelta) else decimalFormatter.to0Decimal(displayDelta)
    }
}
