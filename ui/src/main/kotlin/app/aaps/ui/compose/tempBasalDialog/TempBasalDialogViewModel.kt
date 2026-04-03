package app.aaps.ui.compose.tempBasalDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.objects.constraints.ConstraintObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class TempBasalDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val uel: UserEntryLogger,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<TempBasalDialogUiState>
        field = MutableStateFlow(TempBasalDialogUiState())

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
    }

    val sideEffect: SharedFlow<SideEffect>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private var cachedProfile: Profile? = null

    init {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val isPercentPump = pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT

        uiState.update {
            TempBasalDialogUiState(
                basalPercent = 100.0,
                basalAbsolute = 0.0,
                durationMinutes = pumpDescription.tempDurationStep.toDouble(),
                isPercentPump = isPercentPump,
                maxTempPercent = pumpDescription.maxTempPercent.toDouble(),
                tempPercentStep = pumpDescription.tempPercentStep.toDouble(),
                maxTempAbsolute = pumpDescription.maxTempAbsolute,
                tempAbsoluteStep = pumpDescription.tempAbsoluteStep,
                tempDurationStep = pumpDescription.tempDurationStep.toDouble(),
                tempMaxDuration = pumpDescription.tempMaxDuration.toDouble(),
            )
        }
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            cachedProfile = profile
            val currentBasal = profile?.getBasal() ?: 0.0
            uiState.update { it.copy(basalAbsolute = currentBasal) }
        }
    }

    fun updateBasalPercent(value: Double) {
        val clamped = value.coerceIn(0.0, uiState.value.maxTempPercent)
        uiState.update { it.copy(basalPercent = clamped) }
    }

    fun updateBasalAbsolute(value: Double) {
        val clamped = value.coerceIn(0.0, uiState.value.maxTempAbsolute)
        uiState.update { it.copy(basalAbsolute = clamped) }
    }

    fun updateDuration(value: Double) {
        val clamped = value.coerceIn(uiState.value.tempDurationStep, uiState.value.tempMaxDuration)
        uiState.update { it.copy(durationMinutes = clamped) }
    }

    fun hasAction(): Boolean {
        val state = uiState.value
        return state.durationMinutes > 0
    }

    private var confirmedState: TempBasalDialogUiState? = null

    fun buildConfirmationSummary(): List<String> {
        val state = uiState.value
        confirmedState = state
        val profile = cachedProfile ?: return emptyList()
        val lines = mutableListOf<String>()
        val durationInMinutes = state.durationMinutes.toInt()

        if (state.isPercentPump) {
            val basalPercentInput = state.basalPercent.toInt()
            val percent = constraintChecker.applyBasalPercentConstraints(
                ConstraintObject(basalPercentInput, aapsLogger), profile
            ).value()
            lines.add(rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": $percent%")
            lines.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
            if (percent != basalPercentInput) lines.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied))
        } else {
            val basalAbsoluteInput = state.basalAbsolute
            val absolute = constraintChecker.applyBasalConstraints(
                ConstraintObject(basalAbsoluteInput, aapsLogger), profile
            ).value()
            lines.add(rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": " + rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, absolute))
            lines.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
            if (abs(absolute - basalAbsoluteInput) > 0.01) lines.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied))
        }

        return lines
    }

    fun confirmAndSave() {
        viewModelScope.launch { confirmAndSaveSuspend() }
    }

    private suspend fun confirmAndSaveSuspend() {
        val state = confirmedState ?: return
        val profile = profileFunction.getProfile() ?: return
        val durationInMinutes = state.durationMinutes.toInt()

        val callback = object : Callback() {
            override fun run() {
                if (!result.success) {
                    sideEffect.tryEmit(SideEffect.ShowDeliveryError(result.comment))
                }
            }
        }

        if (state.isPercentPump) {
            val percent = constraintChecker.applyBasalPercentConstraints(
                ConstraintObject(state.basalPercent.toInt(), aapsLogger), profile
            ).value()
            uel.log(
                action = Action.TEMP_BASAL, source = Sources.TempBasalDialog,
                listValues = listOf(
                    ValueWithUnit.Percent(percent),
                    ValueWithUnit.Minute(durationInMinutes)
                )
            )
            commandQueue.tempBasalPercent(percent, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
        } else {
            val absolute = constraintChecker.applyBasalConstraints(
                ConstraintObject(state.basalAbsolute, aapsLogger), profile
            ).value()
            uel.log(
                action = Action.TEMP_BASAL, source = Sources.TempBasalDialog,
                listValues = listOf(
                    ValueWithUnit.Insulin(absolute),
                    ValueWithUnit.Minute(durationInMinutes)
                )
            )
            commandQueue.tempBasalAbsolute(absolute, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
        }
    }
}
