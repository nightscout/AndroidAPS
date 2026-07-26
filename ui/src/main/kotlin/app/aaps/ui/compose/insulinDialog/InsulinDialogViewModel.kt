package app.aaps.ui.compose.insulinDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
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
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
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
import kotlin.math.max

@HiltViewModel
@Stable
class InsulinDialogViewModel @Inject constructor(
    constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    activePlugin: ActivePlugin,
    val activeInsulin: Insulin,
    private val ch: ConcentrationHelper,
    val insulinManager: InsulinManager,
    val config: Config,
    private val automation: Automation,
    val decimalFormatter: DecimalFormatter,
    private val loop: Loop,
    val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    hardLimits: HardLimits,
    private val batchExecutor: BatchExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsulinDialogUiState())
    val uiState: StateFlow<InsulinDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data object ShowNoActionDialog : SideEffect()

        /** The MASTER prepared the batch and returned its merged confirmation [lines]; show them, then [commit] [bolusId]. */
        data class ShowConfirmation(val bolusId: Long, val lines: List<ConfirmationLine>) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val now = dateUtil.now()
        val pump = activePlugin.activePump
        val constrainedMax = constraintChecker.getMaxBolusAllowed().value()
        val maxInsulin = if (constrainedMax > 0.0) constrainedMax else hardLimits.maxBolus()
        val bolusStep = pump.pumpDescription.bolusStep
        val units = profileFunction.getUnits()

        // Conservative default for medical dosing UI: start in record-only mode and relax
        // only after the async loop.runningMode() check below confirms the loop is actually
        // running. Erring on "real delivery" first and flipping to "record-only" later would
        // (a) visibly toggle the checkbox under the user, and (b) let a fast-tapping user
        // confirm a "deliver" dialog that the Loop guard would then reject. Safer to start
        // checked and uncheck once we know the loop is running.
        val pumpInitialized = pump.isInitialized()
        val isAapsClient = config.AAPSCLIENT
        val initialForcedRecordOnly = true

        _uiState.update {
            InsulinDialogUiState(
                insulin = 0.0,
                timeOffsetMinutes = 0,
                eatingSoonTtChecked = false,
                recordOnlyChecked = initialForcedRecordOnly,
                notes = "",
                eventTime = now,
                eventTimeOriginal = now,
                insulins = insulinManager.insulins.toList(),
                maxInsulin = maxInsulin,
                bolusStep = bolusStep,
                insulinButtonIncrement1 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                insulinButtonIncrement2 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                insulinButtonIncrement3 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement3),
                eatingSoonTtTarget = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.EATING_SOON), units),
                eatingSoonTtDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON),
                units = units,
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode),
                isAapsClient = isAapsClient,
                forcedRecordOnly = initialForcedRecordOnly
            )
        }
        viewModelScope.launch {
            val runningIcfg = getRunningIcfg()
            val mode = loop.runningMode()
            val cantDeliverBolus = PumpCommandGate.check(mode, PumpCommandGate.CommandKind.BOLUS) is PumpCommandGate.Decision.Reject
            // A client delivers via the master (the batch round-trip), so its local pump state must NOT force
            // record-only; only a master's own can't-deliver conditions do. The record-only toggle still works.
            val forcedRecordOnly = if (isAapsClient) false else (cantDeliverBolus || !pumpInitialized)
            _uiState.update {
                it.copy(
                    selectedIcfg = runningIcfg,
                    forcedRecordOnly = forcedRecordOnly,
                    recordOnlyChecked = forcedRecordOnly
                )
            }
        }
    }

    private suspend fun getRunningIcfg() = profileFunction.getProfile()?.iCfg ?: activeInsulin.iCfg

    fun refreshInsulinButtons() {
        _uiState.update {
            it.copy(
                insulinButtonIncrement1 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                insulinButtonIncrement2 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                insulinButtonIncrement3 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement3)
            )
        }
    }

    fun updateInsulin(value: Double) {
        val clamped = value.coerceIn(0.0, uiState.value.maxInsulin)
        _uiState.update { it.copy(insulin = clamped) }
    }

    fun addInsulin(increment: Double) {
        val state = uiState.value
        // Snap to the deliverable bolus step so a preset increment that isn't a step multiple (e.g. a 0.25 U
        // button on U200, step 0.10) lands on a deliverable value. ch.bolusStep is amount-aware (Insight) and
        // concentration-adjusted; evaluated at the target so it matches the pump's grid there. Floor, never up.
        val target = max(0.0, state.insulin + increment).coerceAtMost(state.maxInsulin)
        val newValue = Round.floorTo(target, ch.bolusStep(target))
        _uiState.update { it.copy(insulin = newValue) }
    }

    fun updateTimeOffset(minutes: Int) {
        val clamped = minutes.coerceIn(-12 * 60, 12 * 60)
        val state = uiState.value
        val newEventTime = state.eventTimeOriginal + clamped.toLong() * 60 * 1000
        _uiState.update {
            it.copy(
                timeOffsetMinutes = clamped,
                eventTime = newEventTime
            )
        }
    }

    fun updateEatingSoonTt(checked: Boolean) {
        _uiState.update { it.copy(eatingSoonTtChecked = checked) }
    }

    fun updateRecordOnly(checked: Boolean) {
        _uiState.update { it.copy(recordOnlyChecked = checked) }
        if (!checked)
            viewModelScope.launch {
                val runningIcfg = getRunningIcfg()
                _uiState.update { it.copy(selectedIcfg = runningIcfg) }
            }
    }

    fun selectInsulinType(iCfg: ICfg) {
        _uiState.update { it.copy(selectedIcfg = iCfg) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun updateEventTime(timeMillis: Long) {
        val state = uiState.value
        val newOffset = ((timeMillis - state.eventTimeOriginal) / (1000 * 60)).toInt()
        _uiState.update {
            it.copy(
                eventTime = timeMillis,
                timeOffsetMinutes = newOffset
            )
        }
    }

    @Volatile private var confirmedState: InsulinDialogUiState? = null

    /**
     * Tap-confirm → ask the MASTER to PREPARE the batch (cap + build the merged confirmation). Client = signed
     * round-trip; master = local. The user then confirms the MASTER's exact lines (the contract — the client never
     * builds or caps its own numbers) and [commit] delivers. appScope: the screen may navigate away (cancelling
     * viewModelScope) while the round-trip is in flight.
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
            when (val prepared = batchExecutor.prepare(actions, Sources.InsulinDialog, rh.gs(app.aaps.core.ui.R.string.bolus))) {
                is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                // Offline block (and a master-local failure) surface here; a client round-trip failure already showed
                // on the app-level modal, so only re-surface NotReachable or a master-side detail message.
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

    /** Confirm the master's prepared batch: deliver the parked bundle exactly once. */
    fun commit(bolusId: Long) {
        appScope.launch {
            val state = confirmedState
            val result = batchExecutor.commit(bolusId, Sources.InsulinDialog, rh.gs(app.aaps.core.ui.R.string.bolus))
            // Non-record: remove the bolus reminder on success. Record-only: only when not back/forward-dated.
            if (result is ActionProgress.Applied && (state == null || !state.recordOnlyChecked || state.timeOffsetMinutes == 0))
                automation.removeAutomationEventBolusReminder()
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
     * Build the batch from the dialog state. The bolus carries the user's RAW amount — the MASTER caps it (a client
     * never caps its own numbers); a record-only is kept as given. The eating-soon TT (target-LOWERING) travels with
     * the bolus so the master applies it only if the bolus is accepted (decision B). iCfg matters only for record-only.
     */
    private fun buildActions(state: InsulinDialogUiState): List<BatchAction> {
        val units = profileFunction.getUnits()
        val iCfg = if (state.recordOnlyChecked) state.selectedIcfg ?: activeInsulin.iCfg else null
        return buildList {
            if (state.eatingSoonTtChecked) {
                val mgdl = profileUtil.convertToMgdl(state.eatingSoonTtTarget, units)
                add(BatchAction.TempTarget(reason = TT.Reason.EATING_SOON.text, lowMgdl = mgdl, highMgdl = mgdl, durationMinutes = state.eatingSoonTtDuration, startOffsetMinutes = 0))
            }
            // Floor to the deliverable bolus step so the confirmed amount equals what the pump delivers (the
            // concentration boundary floors the converted cU to the native pulse grid). ch.bolusStep is
            // amount-aware (Insight) + concentration-adjusted. Covers typed entry too.
            val deliverableInsulin = Round.floorTo(state.insulin, ch.bolusStep(state.insulin))
            if (deliverableInsulin > 0)
                add(
                    BatchAction.Bolus(
                        insulin = deliverableInsulin, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                        recordOnly = state.recordOnlyChecked, notes = state.notes, timestamp = state.eventTime, iCfg = iCfg
                    )
                )
        }
    }
}
