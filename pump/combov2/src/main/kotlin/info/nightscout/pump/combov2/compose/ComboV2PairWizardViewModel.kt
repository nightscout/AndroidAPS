package info.nightscout.pump.combov2.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.PAIRING_PIN_SIZE
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.comboctl.base.ProgressStage
import info.nightscout.pump.combov2.ComboV2Plugin
import info.nightscout.pump.combov2.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PairWizardPhase {
    /** Driver not yet initialized — Bluetooth unavailable. */
    DriverNotInitialized,

    /** Ready to begin pairing — showing the intro & "Start pairing" button. */
    Idle,

    /** Pairing in progress (scanning / connecting / handshake / PIN / finishing). */
    InProgress,

    /** Pairing finished successfully. */
    Finished,

    /** Pairing aborted (cancelled, timeout, error). */
    Aborted
}

data class ComboV2PairWizardUiState(
    val phase: PairWizardPhase = PairWizardPhase.DriverNotInitialized,
    val stepDescription: String = "",
    val overallProgress: Float = 0f,
    val scanningIndeterminate: Boolean = false,
    val pinEntryVisible: Boolean = false,
    val pinText: String = "",
    val pinFailed: Boolean = false,
    val abortReason: String = "",
    val confirmCancel: Boolean = false
)

sealed class PairWizardEvent {
    data object Finish : PairWizardEvent()
}

@HiltViewModel
class ComboV2PairWizardViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val combov2Plugin: ComboV2Plugin
) : ViewModel() {

    private val _events = MutableSharedFlow<PairWizardEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<PairWizardEvent> = _events

    private val _pinText = MutableStateFlow("")
    private val _confirmCancel = MutableStateFlow(false)

    /**
     * Combined UI state derived from:
     * - driverStateUIFlow (for NotInitialized check)
     * - getPairingProgressFlow() (for stage + progress)
     * - previousPairingAttemptFailedFlow (for PIN failure indicator)
     * - local PIN text + cancel confirmation
     */
    val uiState: StateFlow<ComboV2PairWizardUiState> = combine(
        combov2Plugin.driverStateUIFlow,
        combov2Plugin.getPairingProgressFlow(),
        combov2Plugin.previousPairingAttemptFailedFlow,
        _pinText,
        _confirmCancel
    ) { driverState, progressReport, pinFailed, pinText, confirmCancel ->
        buildState(driverState, progressReport.stage, progressReport.overallProgress, pinFailed, pinText, confirmCancel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComboV2PairWizardUiState())

    private fun buildState(
        driverState: ComboV2Plugin.DriverState,
        stage: ProgressStage,
        overallProgress: Double,
        pinFailed: Boolean,
        pinText: String,
        confirmCancel: Boolean
    ): ComboV2PairWizardUiState {
        if (driverState == ComboV2Plugin.DriverState.NotInitialized) {
            return ComboV2PairWizardUiState(phase = PairWizardPhase.DriverNotInitialized)
        }

        val phase = when (stage) {
            BasicProgressStage.Idle       -> PairWizardPhase.Idle
            BasicProgressStage.Finished   -> PairWizardPhase.Finished
            is BasicProgressStage.Aborted -> PairWizardPhase.Aborted
            else                          -> PairWizardPhase.InProgress
        }

        val description = when (stage) {
            BasicProgressStage.ScanningForPumpStage           ->
                rh.gs(R.string.combov2_scanning_for_pump)

            is BasicProgressStage.EstablishingBtConnection    ->
                rh.gs(R.string.combov2_establishing_bt_connection, stage.currentAttemptNr)

            BasicProgressStage.PerformingConnectionHandshake  ->
                rh.gs(R.string.combov2_pairing_performing_handshake)

            BasicProgressStage.ComboPairingKeyAndPinRequested ->
                rh.gs(R.string.combov2_pairing_pump_requests_pin)

            BasicProgressStage.ComboPairingFinishing          ->
                rh.gs(R.string.combov2_pairing_finishing)

            else                                              -> ""
        }

        val abortReason = if (stage is BasicProgressStage.Aborted) {
            when (stage) {
                is BasicProgressStage.Cancelled -> rh.gs(R.string.combov2_pairing_cancelled)
                is BasicProgressStage.Timeout   -> rh.gs(R.string.combov2_pairing_combo_scan_timeout_reached)
                is BasicProgressStage.Error     -> rh.gs(R.string.combov2_pairing_failed_due_to_error, stage.cause.toString())
                else                            -> rh.gs(R.string.combov2_pairing_aborted_unknown_reasons)
            }
        } else ""

        return ComboV2PairWizardUiState(
            phase = phase,
            stepDescription = description,
            overallProgress = overallProgress.toFloat().coerceIn(0f, 1f),
            scanningIndeterminate = stage == BasicProgressStage.ScanningForPumpStage,
            pinEntryVisible = stage == BasicProgressStage.ComboPairingKeyAndPinRequested,
            pinText = pinText,
            pinFailed = pinFailed,
            abortReason = abortReason,
            confirmCancel = confirmCancel
        )
    }

    fun reset() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: reset")
        _pinText.value = ""
        _confirmCancel.value = false
    }

    fun startPairing() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: start pairing")
        _pinText.value = ""
        combov2Plugin.startPairing()
    }

    fun requestCancelPairing() {
        _confirmCancel.value = true
    }

    fun dismissCancelConfirmation() {
        _confirmCancel.value = false
    }

    fun confirmCancelPairing() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: cancel confirmed")
        _confirmCancel.value = false
        combov2Plugin.cancelPairing()
    }

    fun onPinTextChange(text: String) {
        val digitsOnly = text.filter { it.isDigit() }.take(PAIRING_PIN_SIZE)
        _pinText.value = digitsOnly
    }

    fun submitPin(): Boolean {
        val digits = _pinText.value
        if (digits.length != PAIRING_PIN_SIZE) return false
        aapsLogger.debug(LTag.PUMP, "PairWizard: submitting PIN")
        _pinText.value = ""
        val pin = PairingPIN(digits.map { it - '0' }.toIntArray())
        viewModelScope.launch {
            combov2Plugin.providePairingPIN(pin)
        }
        return true
    }

    /**
     * Cancels an ongoing pairing if one is in progress. Called when the user
     * leaves the wizard (back arrow, onFinish, or teardown via [onCleared]).
     * Idempotent and safe to call at any phase.
     */
    fun cancelIfInProgress() {
        if (combov2Plugin.driverStateUIFlow.value == ComboV2Plugin.DriverState.NotInitialized) return
        val stage = combov2Plugin.getPairingProgressFlow().value.stage
        val terminal = stage == BasicProgressStage.Idle ||
            stage == BasicProgressStage.Finished ||
            stage is BasicProgressStage.Aborted
        if (!terminal) {
            aapsLogger.debug(LTag.PUMP, "PairWizard: cancelling in-progress pairing on teardown")
            combov2Plugin.cancelPairing()
        }
    }

    fun acknowledgeCompletion() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: acknowledge and finish")
        if (combov2Plugin.driverStateUIFlow.value != ComboV2Plugin.DriverState.NotInitialized) {
            val stage = combov2Plugin.getPairingProgressFlow().value.stage
            if (stage == BasicProgressStage.Finished || stage is BasicProgressStage.Aborted) {
                combov2Plugin.resetPairingProgress()
            }
        }
        _events.tryEmit(PairWizardEvent.Finish)
    }

    fun onDriverNotInitializedGoBack() {
        aapsLogger.debug(LTag.PUMP, "PairWizard: driver not initialized, going back")
        _events.tryEmit(PairWizardEvent.Finish)
    }

    override fun onCleared() {
        // If torn down mid-pairing, cancel the in-flight attempt; on terminal
        // stages, reset the progress flow so future pairings start clean.
        cancelIfInProgress()
        if (combov2Plugin.driverStateUIFlow.value != ComboV2Plugin.DriverState.NotInitialized) {
            val stage = combov2Plugin.getPairingProgressFlow().value.stage
            if (stage == BasicProgressStage.Finished || stage is BasicProgressStage.Aborted) {
                combov2Plugin.resetPairingProgress()
            }
        }
        super.onCleared()
    }
}
