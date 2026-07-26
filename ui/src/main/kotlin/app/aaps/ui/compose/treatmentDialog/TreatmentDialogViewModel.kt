package app.aaps.ui.compose.treatmentDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.objects.runningMode.PumpCommandGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
class TreatmentDialogViewModel @Inject constructor(
    constraintChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    private val activeInsulin: Insulin,
    private val ch: ConcentrationHelper,
    private val config: Config,
    val decimalFormatter: DecimalFormatter,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    hardLimits: HardLimits,
    private val loop: Loop,
    private val batchExecutor: BatchExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(TreatmentDialogUiState())
    val uiState: StateFlow<TreatmentDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data object ShowNoActionDialog : SideEffect()

        /** The MASTER prepared the treatment and returned its merged confirmation [lines]; show them, then [commit] [bolusId]. */
        data class ShowConfirmation(val bolusId: Long, val lines: List<ConfirmationLine>) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val pump = activePlugin.activePump
        val constrainedMax = constraintChecker.getMaxBolusAllowed().value()
        val maxInsulin = if (constrainedMax > 0.0) constrainedMax else hardLimits.maxBolus()
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val bolusStep = pump.pumpDescription.bolusStep
        val isAapsClient = config.AAPSCLIENT
        // Conservative default: start in record-only mode and relax only after the async
        // loop.runningMode() check below confirms the loop is actually running. Avoids
        // letting a fast-tapping user submit a real-delivery flow that the guard would
        // then reject; matches InsulinDialog's pattern.
        val pumpInitialized = pump.isInitialized()

        _uiState.update {
            TreatmentDialogUiState(
                insulin = 0.0,
                carbs = 0,
                maxInsulin = maxInsulin,
                maxCarbs = maxCarbs,
                bolusStep = bolusStep,
                isAapsClient = isAapsClient,
                forcedRecordOnly = true
            )
        }
        viewModelScope.launch {
            val mode = loop.runningMode()
            val cantDeliverBolus = PumpCommandGate.check(mode, PumpCommandGate.CommandKind.BOLUS) is PumpCommandGate.Decision.Reject
            // A client delivers via the master (see confirmRemoteBolus), so its local pump state must NOT force
            // record-only; only a master's own can't-deliver conditions do.
            val forcedRecordOnly = if (isAapsClient) false else (cantDeliverBolus || !pumpInitialized)
            _uiState.update { it.copy(forcedRecordOnly = forcedRecordOnly) }
        }
    }

    fun updateInsulin(value: Double) {
        _uiState.update { it.copy(insulin = value) }
    }

    fun updateCarbs(value: Int) {
        _uiState.update { it.copy(carbs = value) }
    }

    @Volatile private var confirmedState: TreatmentDialogUiState? = null

    /**
     * Tap-confirm → ask the MASTER to PREPARE the treatment (cap + build the merged confirmation). Client = signed
     * round-trip; master = local. The user confirms the MASTER's exact lines (the contract — the client never caps its
     * own numbers) and [commit] delivers. A forced record-only (master can't deliver) records insulin + carbs as given.
     */
    fun prepareAndConfirm() {
        appScope.launch {
            val state = uiState.value
            confirmedState = state
            val actions = buildActions(state)
            if (actions.isEmpty()) {
                _sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
                return@launch
            }
            when (val prepared = batchExecutor.prepare(actions, Sources.TreatmentDialog, rh.gs(app.aaps.core.ui.R.string.bolus))) {
                is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                is ActionProgress.Rejected -> when (prepared.reason) {
                    FailureReason.NotReachable, FailureReason.ControlDisabled -> rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.bolus), message = rh.gs(prepared.reason.failTextResId())))
                    // No-op after caps (e.g. the bolus was constraint-capped to 0): neutral message, NOT the bolus-error alarm.
                    FailureReason.NoAction     -> _sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
                    else                       -> prepared.detail?.let { detail ->
                        if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.bolus), message = detail))
                        else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                    }
                }

                else                       -> Unit // Unconfirmed → app-level modal
            }
        }
    }

    /** Confirm the master's prepared treatment: deliver/record the parked bundle exactly once. */
    fun commit(bolusId: Long) {
        appScope.launch {
            val result = batchExecutor.commit(bolusId, Sources.TreatmentDialog, rh.gs(app.aaps.core.ui.R.string.bolus))
            // Surface a failed commit. NotReachable → the offline message; any other Rejected (ExecutionFailed,
            // NoPendingBolus, …) → the master's detail. Unconfirmed (state unknown) rides the round-trip's app-level modal.
            if (result is ActionProgress.Rejected) {
                if (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled)
                    rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.bolus), message = rh.gs(result.reason.failTextResId())))
                else result.detail?.let { detail ->
                    if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.bolus), message = detail))
                    else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                }
            }
        }
    }

    /**
     * Build the batch — one fixed Bolus carrying the user's RAW insulin + carbs (the MASTER caps both, classifies the
     * event type, and either delivers or records). A forced record-only (the master can't deliver) is persisted as
     * given with the resolved insulin config. No TT, no offset/duration; the dialog has no event-time picker, so the
     * record is stamped "now" (timestamp 0 = now in the executor).
     */
    private suspend fun buildActions(state: TreatmentDialogUiState): List<BatchAction> {
        val iCfg = if (state.forcedRecordOnly) profileFunction.getProfile()?.iCfg ?: activeInsulin.iCfg else null
        // Floor to the deliverable bolus step so the confirmed amount equals what the pump delivers (the
        // concentration boundary floors the converted cU to the native pulse grid). ch.bolusStep is
        // amount-aware (Insight) + concentration-adjusted.
        val deliverableInsulin = Round.floorTo(state.insulin, ch.bolusStep(state.insulin))
        return buildList {
            if (deliverableInsulin > 0.0 || state.carbs > 0)
                add(
                    BatchAction.Bolus(
                        insulin = deliverableInsulin, carbs = state.carbs, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                        recordOnly = state.forcedRecordOnly, notes = "", timestamp = 0L, iCfg = iCfg
                    )
                )
        }
    }
}
