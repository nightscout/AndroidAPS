package app.aaps.ui.compose.tempBasalDialog

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
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
class TempBasalDialogViewModel @Inject constructor(
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val rh: ResourceHelper,
    private val batchExecutor: BatchExecutor,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(TempBasalDialogUiState())
    val uiState: StateFlow<TempBasalDialogUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        // On a client the VirtualPump mirrors the master's pump (RunningConfiguration), so the style + ranges below
        // are already the master's. The master re-validates at prepare and refuses if the client's config is stale.
        val pumpDescription = activePlugin.activePump.pumpDescription
        val isPercentPump = pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
        val profile = profileFunction.getProfile()
        val currentBasal = profile?.getBasal() ?: 0.0

        _uiState.update {
            TempBasalDialogUiState(
                basalPercent = 100.0,
                basalAbsolute = currentBasal,
                durationMinutes = pumpDescription.tempDurationStep.toDouble(),
                isPercentPump = isPercentPump,
                maxTempPercent = pumpDescription.maxTempPercent.toDouble(),
                tempPercentStep = pumpDescription.tempPercentStep.toDouble(),
                maxTempAbsolute = pumpDescription.maxTempAbsolute,
                tempAbsoluteStep = pumpDescription.tempAbsoluteStep,
                tempDurationStep = pumpDescription.tempDurationStep.toDouble(),
                tempMaxDuration = pumpDescription.tempMaxDuration.toDouble(),
                profile = profile,
            )
        }
    }

    fun updateBasalPercent(value: Double) {
        _uiState.update { it.copy(basalPercent = value) }
    }

    fun updateBasalAbsolute(value: Double) {
        _uiState.update { it.copy(basalAbsolute = value) }
    }

    fun updateDuration(value: Double) {
        _uiState.update { it.copy(durationMinutes = value) }
    }

    /**
     * Tap-confirm → ask the MASTER to PREPARE (validate against its pump, cap, author the confirmation). Client = signed
     * round-trip; master = local. The user then confirms the MASTER's exact lines and [commit] applies it. appScope: the
     * screen may navigate away (cancelling viewModelScope) while the round-trip is in flight.
     */
    fun prepareAndConfirm() {
        // Ignore re-taps while a prepare is already in flight; the screen also disables Confirm via isPreparing.
        if (uiState.value.isPreparing) return
        appScope.launch {
            val state = uiState.value
            if (state.durationMinutes <= 0.0) {
                _sideEffect.tryEmit(SideEffect.ShowNoActionDialog)
                return@launch
            }
            _uiState.update { it.copy(isPreparing = true) }
            try {
                val rate = if (state.isPercentPump) state.basalPercent else state.basalAbsolute
                val action = BatchAction.TempBasal(rate = rate, isPercent = state.isPercentPump, durationMinutes = state.durationMinutes.toInt())
                val label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label)
                when (val prepared = batchExecutor.prepare(listOf(action), Sources.TempBasalDialog, label)) {
                    is ActionProgress.Prepared -> _sideEffect.tryEmit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                    // Offline block (and a master-local failure) surface here; a client round-trip failure already showed on the modal.
                    is ActionProgress.Rejected ->
                        if (prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), message = rh.gs(prepared.reason.failTextResId())))
                        else prepared.detail?.let { detail ->
                            if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), message = detail))
                            else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                        }

                    else                       -> Unit // Unconfirmed → app-level modal
                }
            } finally {
                _uiState.update { it.copy(isPreparing = false) }
            }
        }
    }

    /** Confirm the master's prepared temp basal: apply it exactly once. A master-local apply failure (pump comms) surfaces here. */
    fun commit(bolusId: Long) {
        appScope.launch {
            val result = batchExecutor.commit(bolusId, Sources.TempBasalDialog, rh.gs(app.aaps.core.ui.R.string.tempbasal_label), pumpDirect = true)
            if (result is ActionProgress.Rejected)
                if (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), message = rh.gs(result.reason.failTextResId())))
                else result.detail?.let { detail ->
                    if (config.AAPSCLIENT) rxBus.send(EventShowDialog.Ok(title = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), message = detail))
                    else _sideEffect.tryEmit(SideEffect.ShowDeliveryError(detail))
                }
        }
    }
}
