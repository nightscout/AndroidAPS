package app.aaps.ui.compose.carbsDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
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
class CarbsDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val profileUtil: ProfileUtil,
    private val iobCobCalculator: IobCobCalculator,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val automation: Automation,
    private val batchExecutor: BatchExecutor,
    private val persistenceLayer: PersistenceLayer,
    val preferences: Preferences,
    val config: Config,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarbsDialogUiState())
    val uiState: StateFlow<CarbsDialogUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        val now = dateUtil.now()
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val units = profileUtil.units

        // Bolus reminder: visible when preference enabled AND predicted BG is low
        val showBolusReminder = if (preferences.get(BooleanKey.OverviewUseBolusReminder)) {
            val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            glucoseStatus != null && glucoseStatus.glucose + 3 * glucoseStatus.delta < 70.0
        } else false

        // Auto-detect hypo condition
        val autoHypo = detectAutoHypo(now)

        _uiState.update {
            CarbsDialogUiState(
                carbs = 0,
                timeOffsetMinutes = 0,
                durationHours = 0,
                hypoTtChecked = autoHypo,
                eatingSoonTtChecked = false,
                activityTtChecked = false,
                alarmChecked = false,
                bolusReminderChecked = false,
                notes = "",
                eventTime = now,
                eventTimeOriginal = now,
                maxCarbs = maxCarbs,
                carbsButtonIncrement1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1),
                carbsButtonIncrement2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2),
                carbsButtonIncrement3 = preferences.get(IntKey.OverviewCarbsButtonIncrement3),
                units = units,
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                showBolusReminder = showBolusReminder,
                hypoTtTarget = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA), units),
                hypoTtDuration = preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA),
                eatingSoonTtTarget = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.EATING_SOON), units),
                eatingSoonTtDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON),
                activityTtTarget = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.ACTIVITY), units),
                activityTtDuration = preferences.ttDurationMinutes(TT.Reason.ACTIVITY),
                maxCarbsDurationHours = HardLimits.MAX_CARBS_DURATION_HOURS,
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode)
            )
        }
    }

    private suspend fun detectAutoHypo(now: Long): Boolean {
        val bgReading = iobCobCalculator.ads.actualBg() ?: return false
        if (bgReading.recalculated >= 72) return false

        val hypoTTDuration = preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA)

        val activeTT = try {
            persistenceLayer.getTemporaryTargetActiveAt(now)
        } catch (_: Exception) {
            null
        }

        if (activeTT != null) {
            val activeTarget = activeTT.highTarget
            val remainingDurationMin = ((activeTT.timestamp + activeTT.duration) - now) / 60000
            if (activeTarget > Constants.NORMAL_TARGET_MGDL && remainingDurationMin > hypoTTDuration) {
                return false
            }
        }

        return true
    }

    fun refreshCarbsButtons() {
        _uiState.update {
            it.copy(
                carbsButtonIncrement1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1),
                carbsButtonIncrement2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2),
                carbsButtonIncrement3 = preferences.get(IntKey.OverviewCarbsButtonIncrement3)
            )
        }
    }

    fun updateCarbs(value: Int) {
        val state = uiState.value
        val clamped = value.coerceIn(-state.maxCarbs, state.maxCarbs)
        _uiState.update { it.copy(carbs = clamped) }
    }

    fun addCarbs(increment: Int) {
        val state = uiState.value
        val newValue = max(0, state.carbs + increment).coerceAtMost(state.maxCarbs)
        _uiState.update { it.copy(carbs = newValue) }
    }

    fun updateTimeOffset(minutes: Int) {
        val clamped = minutes.coerceIn(-7 * 24 * 60, 12 * 60)
        val state = uiState.value
        val newEventTime = state.eventTimeOriginal + clamped.toLong() * 60 * 1000
        _uiState.update {
            it.copy(
                timeOffsetMinutes = clamped,
                eventTime = newEventTime
            )
        }
    }

    fun updateDuration(hours: Int) {
        val clamped = hours.coerceIn(0, uiState.value.maxCarbsDurationHours.toInt())
        _uiState.update { it.copy(durationHours = clamped) }
    }

    fun updateHypoTt(checked: Boolean) {
        _uiState.update {
            it.copy(
                hypoTtChecked = checked,
                eatingSoonTtChecked = if (checked) false else it.eatingSoonTtChecked,
                activityTtChecked = if (checked) false else it.activityTtChecked
            )
        }
    }

    fun updateEatingSoonTt(checked: Boolean) {
        _uiState.update {
            it.copy(
                eatingSoonTtChecked = checked,
                hypoTtChecked = if (checked) false else it.hypoTtChecked,
                activityTtChecked = if (checked) false else it.activityTtChecked
            )
        }
    }

    fun updateActivityTt(checked: Boolean) {
        _uiState.update {
            it.copy(
                activityTtChecked = checked,
                hypoTtChecked = if (checked) false else it.hypoTtChecked,
                eatingSoonTtChecked = if (checked) false else it.eatingSoonTtChecked
            )
        }
    }

    fun updateAlarm(checked: Boolean) {
        _uiState.update { it.copy(alarmChecked = checked) }
    }

    fun updateBolusReminder(checked: Boolean) {
        _uiState.update { it.copy(bolusReminderChecked = checked) }
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

    @Volatile private var confirmedState: CarbsDialogUiState? = null

    /**
     * Tap-confirm → ask the MASTER to PREPARE the batch (cap + COB-clamp + build the merged confirmation). Client =
     * signed round-trip; master = local. The user confirms the MASTER's exact lines (the contract — the client never
     * caps its own carbs) and [commit] records them.
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
            when (val prepared = batchExecutor.prepare(actions, Sources.CarbDialog, rh.gs(app.aaps.core.ui.R.string.carbs))) {
                is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                is ActionProgress.Rejected ->
                    if (prepared.reason == FailureReason.NotReachable) _sideEffect.tryEmit(SideEffect.ShowDeliveryError(rh.gs(app.aaps.core.ui.R.string.clientcontrol_fail_not_reachable)))
                    else prepared.detail?.let { _sideEffect.tryEmit(SideEffect.ShowDeliveryError(it)) }

                else                       -> Unit // Unconfirmed → app-level modal
            }
        }
    }

    /** Confirm the master's prepared batch: record the carbs (+ TT) once, then the device-local reminders. */
    fun commit(bolusId: Long) {
        appScope.launch {
            val state = confirmedState
            val result = batchExecutor.commit(bolusId, Sources.CarbDialog, rh.gs(app.aaps.core.ui.R.string.carbs))
            // Opt-in post-carbs bolus reminder (device-local) on success.
            if (result is ActionProgress.Applied && preferences.get(BooleanKey.OverviewUseBolusReminder) && state?.bolusReminderChecked == true)
                automation.scheduleAutomationEventBolusReminder()
            // Surface a failed commit. NotReachable → the offline message; any other Rejected (ExecutionFailed,
            // NoPendingBolus, …) → the master's detail. Unconfirmed (state unknown) rides the round-trip's app-level modal.
            if (result is ActionProgress.Rejected) {
                if (result.reason == FailureReason.NotReachable)
                    _sideEffect.tryEmit(SideEffect.ShowDeliveryError(rh.gs(app.aaps.core.ui.R.string.clientcontrol_fail_not_reachable)))
                else result.detail?.let { _sideEffect.tryEmit(SideEffect.ShowDeliveryError(it)) }
            }
            automation.removeAutomationEventEatReminder()
            // Device-local "time to eat" alarm — on confirm, when configured.
            if (state != null && state.alarmChecked && state.carbs > 0 && state.timeOffsetMinutes > 0)
                automation.scheduleTimeToEatReminder(T.mins(state.timeOffsetMinutes.toLong()).secs().toInt())
        }
    }

    /**
     * Build the batch from the dialog state. Carbs travel RAW — the MASTER caps + COB-clamps (so the client never
     * decides its own number). The TT (target-raising for hypo/activity, lowering for eating-soon) travels WITH the
     * carbs so the master applies it in one transaction; it starts now even when the carbs are back-dated.
     */
    private fun buildActions(state: CarbsDialogUiState): List<BatchAction> {
        val reason = when {
            state.activityTtChecked   -> TT.Reason.ACTIVITY
            state.eatingSoonTtChecked -> TT.Reason.EATING_SOON
            state.hypoTtChecked       -> TT.Reason.HYPOGLYCEMIA
            else                      -> TT.Reason.CUSTOM
        }
        val ttDuration = when {
            state.activityTtChecked   -> state.activityTtDuration
            state.eatingSoonTtChecked -> state.eatingSoonTtDuration
            state.hypoTtChecked       -> state.hypoTtDuration
            else                      -> 0
        }
        val ttTarget = when {
            state.activityTtChecked   -> state.activityTtTarget
            state.eatingSoonTtChecked -> state.eatingSoonTtTarget
            state.hypoTtChecked       -> state.hypoTtTarget
            else                      -> 0.0
        }
        return buildList {
            if (reason != TT.Reason.CUSTOM) {
                val mgdl = profileUtil.convertToMgdl(ttTarget, state.units)
                add(BatchAction.TempTarget(reason = reason.text, lowMgdl = mgdl, highMgdl = mgdl, durationMinutes = ttDuration, startOffsetMinutes = 0))
            }
            if (state.carbs != 0)
                add(
                    BatchAction.Bolus(
                        insulin = 0.0, carbs = state.carbs, carbsTimeOffsetMinutes = state.timeOffsetMinutes,
                        carbsDurationHours = state.durationHours, recordOnly = false, notes = state.notes, timestamp = state.eventTime, iCfg = null
                    )
                )
        }
    }
}
