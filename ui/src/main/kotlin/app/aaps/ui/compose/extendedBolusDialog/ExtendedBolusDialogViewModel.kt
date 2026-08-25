package app.aaps.ui.compose.extendedBolusDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
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
class ExtendedBolusDialogViewModel @Inject constructor(
    private val constraintChecker: ConstraintsChecker,
    activePlugin: ActivePlugin,
    private val config: Config,
    private val rh: ResourceHelper,
    private val batchExecutor: BatchExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtendedBolusDialogUiState())
    val uiState: StateFlow<ExtendedBolusDialogUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ShowDeliveryError(val comment: String) : SideEffect()
        data object ShowNoActionDialog : SideEffect()

        /** The MASTER prepared the action and returned its confirmation [lines]; show them, then [commit] [bolusId]. */
        data class ShowConfirmation(val bolusId: Long, val lines: List<ConfirmationLine>) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    init {
        // pumpDescription mirrors the master's pump on a client (RunningConfiguration); the master caps + validates at prepare.
        val pumpDescription = activePlugin.activePump.pumpDescription
        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()

        // Default to showing the loop-stop warning until the async closed-loop check resolves, so a closed-loop user
        // can't briefly see the form and start typing before the warning gate appears.
        _uiState.update {
            ExtendedBolusDialogUiState(
                insulin = pumpDescription.extendedBolusMinAmount,
                durationMinutes = pumpDescription.extendedBolusDurationStep,
                maxInsulin = maxInsulin,
                minInsulin = pumpDescription.extendedBolusMinAmount,
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
        _uiState.update { it.copy(insulin = value) }
    }

    fun updateDuration(value: Double) {
        _uiState.update { it.copy(durationMinutes = value) }
    }

    /**
     * Tap-confirm → ask the MASTER to PREPARE (cap + author the confirmation). Client = signed round-trip; master =
     * local. The user then confirms the MASTER's exact lines and [commit] delivers. appScope: the screen may navigate
     * away (cancelling viewModelScope) while the round-trip is in flight.
     */
    fun prepareAndConfirm() {
        // Ignore re-taps while a prepare is already in flight; the screen also disables Confirm via isPreparing.
        if (uiState.value.isPreparing) return
        appScope.launch {
            val state = uiState.value
            if (state.insulin <= 0.0 || state.durationMinutes <= 0.0) {
                _sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
                return@launch
            }
            _uiState.update { it.copy(isPreparing = true) }
            try {
                val action = BatchAction.ExtendedBolus(insulin = state.insulin, durationMinutes = state.durationMinutes.toInt())
                val label = rh.gs(app.aaps.core.ui.R.string.extended_bolus)
                when (val prepared = batchExecutor.prepare(listOf(action), Sources.ExtendedBolusDialog, label)) {
                    is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                    // Offline block (and a master-local failure) surface here; a client round-trip failure already showed on the modal.
                    is ActionProgress.Rejected ->
                        if (prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.extended_bolus), message = rh.gs(prepared.reason.failTextResId())))
                        else prepared.detail?.let { detail ->
                            if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.extended_bolus), message = detail))
                            else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                        }

                    else                       -> Unit // Unconfirmed → app-level modal
                }
            } finally {
                _uiState.update { it.copy(isPreparing = false) }
            }
        }
    }

    /** Confirm the master's prepared extended bolus: deliver it exactly once. A master-local apply failure (pump comms) surfaces here. */
    fun commit(bolusId: Long) {
        appScope.launch {
            val result = batchExecutor.commit(bolusId, Sources.ExtendedBolusDialog, rh.gs(app.aaps.core.ui.R.string.extended_bolus), pumpDirect = true)
            if (result is ActionProgress.Rejected)
                if (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.extended_bolus), message = rh.gs(result.reason.failTextResId())))
                else result.detail?.let { detail ->
                    if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.extended_bolus), message = detail))
                    else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                }
        }
    }
}
