package app.aaps.ui.compose.insulinDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

@HiltViewModel
@Stable
class InsulinDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val commandQueue: CommandQueue,
    private val activePlugin: ActivePlugin,
    val activeInsulin: Insulin,
    val insulinManager: InsulinManager,
    val config: Config,
    private val automation: Automation,
    private val uel: UserEntryLogger,
    private val persistenceLayer: PersistenceLayer,
    val decimalFormatter: DecimalFormatter,
    loop: Loop,
    val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val hardLimits: HardLimits
) : ViewModel() {

    val uiState: StateFlow<InsulinDialogUiState>
        field = MutableStateFlow(InsulinDialogUiState())

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data object ShowNoActionDialog : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    init {
        val now = dateUtil.now()
        val pump = activePlugin.activePump
        val constrainedMax = constraintChecker.getMaxBolusAllowed().value()
        val maxInsulin = if (constrainedMax > 0.0) constrainedMax else hardLimits.maxBolus()
        val bolusStep = pump.pumpDescription.bolusStep
        val units = profileFunction.getUnits()

        val forcedRecordOnly = loop.runningMode.isSuspended() || !pump.isInitialized()
        val isAapsClient = config.AAPSCLIENT

        uiState.update {
            InsulinDialogUiState(
                insulin = 0.0,
                timeOffsetMinutes = 0,
                eatingSoonTtChecked = false,
                recordOnlyChecked = forcedRecordOnly || isAapsClient,
                notes = "",
                eventTime = now,
                eventTimeOriginal = now,
                insulins = insulinManager.insulins.toList(),
                maxInsulin = maxInsulin,
                bolusStep = bolusStep,
                insulinButtonIncrement1 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                insulinButtonIncrement2 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                insulinButtonIncrement3 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement3),
                eatingSoonTtTarget = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget),
                eatingSoonTtDuration = preferences.get(IntKey.OverviewEatingSoonDuration),
                units = units,
                showNotesFromPreferences = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                simpleMode = preferences.get(BooleanKey.GeneralSimpleMode),
                isAapsClient = isAapsClient,
                forcedRecordOnly = forcedRecordOnly
            )
        }
        viewModelScope.launch {
            val runningIcfg = getRunningIcfg()
            uiState.update { it.copy(selectedIcfg = runningIcfg) }
        }
    }

    private suspend fun getRunningIcfg() = profileFunction.getProfile()?.iCfg ?: activeInsulin.iCfg

    fun refreshInsulinButtons() {
        uiState.update {
            it.copy(
                insulinButtonIncrement1 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                insulinButtonIncrement2 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                insulinButtonIncrement3 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement3)
            )
        }
    }

    fun updateInsulin(value: Double) {
        val clamped = value.coerceIn(0.0, uiState.value.maxInsulin)
        uiState.update { it.copy(insulin = clamped) }
    }

    fun addInsulin(increment: Double) {
        val state = uiState.value
        val newValue = max(0.0, state.insulin + increment).coerceAtMost(state.maxInsulin)
        uiState.update { it.copy(insulin = newValue) }
    }

    fun updateTimeOffset(minutes: Int) {
        val clamped = minutes.coerceIn(-12 * 60, 12 * 60)
        val state = uiState.value
        val newEventTime = state.eventTimeOriginal + clamped.toLong() * 60 * 1000
        uiState.update {
            it.copy(
                timeOffsetMinutes = clamped,
                eventTime = newEventTime
            )
        }
    }

    fun updateEatingSoonTt(checked: Boolean) {
        uiState.update { it.copy(eatingSoonTtChecked = checked) }
    }

    fun updateRecordOnly(checked: Boolean) {
        uiState.update { it.copy(recordOnlyChecked = checked) }
        if (!checked)
            viewModelScope.launch {
                val runningIcfg = getRunningIcfg()
                uiState.update { it.copy(selectedIcfg = runningIcfg) }
            }
    }

    fun selectInsulinType(iCfg: ICfg) {
        uiState.update { it.copy(selectedIcfg = iCfg) }
    }

    fun updateNotes(value: String) {
        uiState.update { it.copy(notes = value) }
    }

    fun updateEventTime(timeMillis: Long) {
        val state = uiState.value
        val newOffset = ((timeMillis - state.eventTimeOriginal) / (1000 * 60)).toInt()
        uiState.update {
            it.copy(
                eventTime = timeMillis,
                timeOffsetMinutes = newOffset
            )
        }
    }

    private var confirmedState: InsulinDialogUiState? = null

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        confirmedState = state
        val lines = mutableListOf<String>()
        val unitLabel = if (state.units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)
        val pump = activePlugin.activePump
        val pumpDescription = pump.pumpDescription

        val insulin = state.insulin
        val insulinAfterConstraints = if (state.recordOnlyChecked) {
            insulin
        } else {
            constraintChecker.applyBolusConstraints(
                ConstraintObject(insulin, aapsLogger)
            ).value()
        }

        // Bolus line
        if (insulinAfterConstraints > 0) {
            lines.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " +
                    decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, pumpDescription.bolusStep)
            )
            if (state.recordOnlyChecked) {
                lines.add(rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only))
                state.selectedIcfg?.let {
                    lines.add(rh.gs(app.aaps.core.ui.R.string.selected_insulin, it.insulinLabel))
                }
            }
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)) {
                lines.add(rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints))
            }
        }

        // Eating soon TT
        if (state.eatingSoonTtChecked) {
            lines.add(
                rh.gs(R.string.temp_target_short) + ": " +
                    decimalFormatter.to1Decimal(state.eatingSoonTtTarget) + " " + unitLabel +
                    " (" + rh.gs(app.aaps.core.ui.R.string.format_mins, state.eatingSoonTtDuration) + ")"
            )
        }

        // Time offset
        val timeOffset = state.timeOffsetMinutes
        if (timeOffset != 0) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(state.eventTime))
        }

        // Notes
        if (state.notes.isNotEmpty()) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + state.notes)
        }

        return lines
    }

    fun hasAction(): Boolean {
        val state = uiState.value
        val insulin = if (state.recordOnlyChecked) {
            state.insulin
        } else {
            constraintChecker.applyBolusConstraints(
                ConstraintObject(state.insulin, aapsLogger)
            ).value()
        }
        return insulin > 0 || state.eatingSoonTtChecked
    }

    fun confirmAndSave() {
        viewModelScope.launch { confirmAndSaveSuspend() }
    }

    private suspend fun confirmAndSaveSuspend() {
        val state = confirmedState ?: return
        val insulin = state.insulin
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(
            ConstraintObject(insulin, aapsLogger)
        ).value()
        val timeOffset = state.timeOffsetMinutes
        val time = state.eventTime
        val notes = state.notes
        val recordOnlyChecked = state.recordOnlyChecked
        val units = profileFunction.getUnits()
        val iCfg = if (state.recordOnlyChecked)
            state.selectedIcfg ?: activeInsulin.iCfg
        else
            getRunningIcfg() ?: activeInsulin.iCfg
        // Insert temp target if eating soon checked
        if (state.eatingSoonTtChecked) {
            val eatingSoonTT = state.eatingSoonTtTarget
            val eatingSoonTTDuration = state.eatingSoonTtDuration
            viewModelScope.launch {
                try {
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = TT(
                            timestamp = System.currentTimeMillis(),
                            duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                            reason = TT.Reason.EATING_SOON,
                            lowTarget = profileUtil.convertToMgdl(eatingSoonTT, units),
                            highTarget = profileUtil.convertToMgdl(eatingSoonTT, units)
                        ),
                        action = Action.TT,
                        source = Sources.InsulinDialog,
                        note = notes,
                        listValues = listOf(
                            ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                            ValueWithUnit.fromGlucoseUnit(eatingSoonTT, units),
                            ValueWithUnit.Minute(eatingSoonTTDuration)
                        )
                    )
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Failed to save temp target", e)
                }
            }
        }

        // Send bolus
        if (insulinAfterConstraints > 0) {
            val detailedBolusInfo = DetailedBolusInfo().also {
                it.eventType = TE.Type.CORRECTION_BOLUS
                it.insulin = insulinAfterConstraints
                it.notes = notes
                it.timestamp = time
            }

            if (recordOnlyChecked) {
                viewModelScope.launch {
                    persistenceLayer.insertOrUpdateBolus(
                        bolus = detailedBolusInfo.createBolus(iCfg),
                        action = Action.BOLUS,
                        source = Sources.InsulinDialog,
                        note = rh.gs(app.aaps.core.ui.R.string.record) + if (notes.isNotEmpty()) ": $notes" else ""
                    )
                }
                if (timeOffset == 0) {
                    automation.removeAutomationEventBolusReminder()
                }
            } else {
                uel.log(
                    Action.BOLUS, Sources.InsulinDialog,
                    notes,
                    ValueWithUnit.Insulin(insulinAfterConstraints)
                )
                commandQueue.bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            sideEffect.tryEmit(SideEffect.ShowDeliveryError(result.comment))
                        } else {
                            automation.removeAutomationEventBolusReminder()
                        }
                    }
                })
            }
        }
    }
}
