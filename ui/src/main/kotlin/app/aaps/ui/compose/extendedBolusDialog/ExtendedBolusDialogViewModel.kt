package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.runningMode.TbrGate
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
import kotlin.math.abs

@HiltViewModel
@Stable
class ExtendedBolusDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val uel: UserEntryLogger,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val runningModeGuard: RunningModeGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtendedBolusDialogUiState())
    val uiState: StateFlow<ExtendedBolusDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()

        // Default to showing the loop-stop warning until the async closed-loop check
        // resolves, so a closed-loop user can't briefly see the form and start typing
        // before the warning gate appears.
        _uiState.update {
            ExtendedBolusDialogUiState(
                insulin = pumpDescription.extendedBolusStep,
                durationMinutes = pumpDescription.extendedBolusDurationStep,
                maxInsulin = maxInsulin,
                extendedStep = pumpDescription.extendedBolusStep,
                extendedDurationStep = pumpDescription.extendedBolusDurationStep,
                extendedMaxDuration = pumpDescription.extendedBolusMaxDuration,
                showLoopStopWarning = true,
                loopStopWarningAccepted = false,
            )
        }
        viewModelScope.launch {
            val isClosedLoop = constraintChecker.isClosedLoopAllowed().value()
            _uiState.update {
                it.copy(
                    showLoopStopWarning = isClosedLoop,
                    loopStopWarningAccepted = !isClosedLoop
                )
            }
        }
    }

    fun acceptLoopStopWarning() {
        _uiState.update { it.copy(loopStopWarningAccepted = true) }
    }

    fun updateInsulin(value: Double) {
        val clamped = value.coerceIn(uiState.value.extendedStep, uiState.value.maxInsulin)
        _uiState.update { it.copy(insulin = clamped) }
    }

    fun updateDuration(value: Double) {
        val clamped = value.coerceIn(uiState.value.extendedDurationStep, uiState.value.extendedMaxDuration)
        _uiState.update { it.copy(durationMinutes = clamped) }
    }

    fun hasAction(): Boolean {
        val state = uiState.value
        val insulinAfterConstraints = constraintChecker.applyExtendedBolusConstraints(
            ConstraintObject(state.insulin, aapsLogger)
        ).value()
        return insulinAfterConstraints > 0 && state.durationMinutes > 0
    }

    private var confirmedState: ExtendedBolusDialogUiState? = null

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        confirmedState = state
        val lines = mutableListOf<String>()
        val durationInMinutes = state.durationMinutes.toInt()

        val insulinAfterConstraints = constraintChecker.applyExtendedBolusConstraints(
            ConstraintObject(state.insulin, aapsLogger)
        ).value()
        lines.add(rh.gs(app.aaps.core.ui.R.string.format_insulin_units, insulinAfterConstraints))
        lines.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraints - state.insulin) > 0.01) {
            lines.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied))
        }

        return lines
    }

    fun confirmAndSave() {
        val state = confirmedState ?: return
        val durationInMinutes = state.durationMinutes.toInt()

        val insulinAfterConstraints = constraintChecker.applyExtendedBolusConstraints(
            ConstraintObject(state.insulin, aapsLogger)
        ).value()

        if (runningModeGuard.checkWithSnackbar(TbrGate.CommandKind.EXTENDED_BOLUS)) return

        uel.log(
            action = Action.EXTENDED_BOLUS, source = Sources.ExtendedBolusDialog,
            listValues = listOf(
                ValueWithUnit.Insulin(insulinAfterConstraints),
                ValueWithUnit.Minute(durationInMinutes)
            )
        )
        commandQueue.extendedBolus(insulinAfterConstraints, durationInMinutes, object : Callback() {
            override fun run() {
                if (!result.success) {
                    _sideEffect.tryEmit(SideEffect.ShowDeliveryError(result.comment))
                }
            }
        })
    }
}
