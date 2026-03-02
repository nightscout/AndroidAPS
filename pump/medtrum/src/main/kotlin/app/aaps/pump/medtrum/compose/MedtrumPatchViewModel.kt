package app.aaps.pump.medtrum.compose

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.encryption.Crypt
import app.aaps.pump.medtrum.services.MedtrumService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PatchEvent {
    data object Finish : PatchEvent()
    data class ShowError(val message: String) : PatchEvent()
}

enum class WizardPage {
    PREPARE, PRIME, ATTACH, ACTIVATE, COMPLETE,
    CONFIRM_DEACTIVATE, DEACTIVATING, DEACTIVATE_COMPLETE,
    RETRY_ACTIVATION
}

@Stable
class MedtrumPatchViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val medtrumPlugin: MedtrumPlugin,
    private val commandQueue: CommandQueue,
    val medtrumPump: MedtrumPump
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val medtrumService: MedtrumService?
        get() = medtrumPlugin.getService()

    // PatchStep state machine — maps 1:1 to the old MedtrumViewModel
    private val _patchStep = MutableStateFlow<PatchStep?>(null)
    val patchStep: StateFlow<PatchStep?> = _patchStep.asStateFlow()

    // SetupStep — internal pump state
    private val _setupStep = MutableStateFlow(SetupStep.INITIAL)
    val setupStep: StateFlow<SetupStep> = _setupStep.asStateFlow()

    // Title string resource ID
    private val _title = MutableStateFlow(R.string.step_prepare_patch)
    val title: StateFlow<Int> = _title.asStateFlow()

    // Wizard page for progress indicator
    private val _wizardPage = MutableStateFlow(WizardPage.PREPARE)
    val wizardPage: StateFlow<WizardPage> = _wizardPage.asStateFlow()

    // Total steps and current step index for progress indicator
    private val _totalSteps = MutableStateFlow(5)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    // One-time events
    private val _events = MutableSharedFlow<PatchEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<PatchEvent> = _events

    private var oldPatchStep: PatchStep? = null
    private var mInitPatchStep: PatchStep? = null
    private var connectRetryCounter = 0

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumPatchViewModel connectionStateFlow: $state")
                if (_patchStep.value != null) {
                    when (state) {
                        ConnectionState.CONNECTED                                 -> {
                            medtrumPump.lastConnection = System.currentTimeMillis()
                        }

                        ConnectionState.DISCONNECTED                              -> {
                            if (_patchStep.value in listOf(
                                    PatchStep.PRIME,
                                    PatchStep.PRIMING,
                                    PatchStep.PRIME_COMPLETE,
                                    PatchStep.ATTACH_PATCH,
                                    PatchStep.ACTIVATE
                                )
                            ) {
                                medtrumService?.connect("Try reconnect from viewModel")
                            }
                            if (_patchStep.value in listOf(PatchStep.PREPARE_PATCH_CONNECT, PatchStep.RETRY_ACTIVATION_CONNECT)) {
                                if (connectRetryCounter < 3) {
                                    connectRetryCounter++
                                    aapsLogger.info(LTag.PUMP, "preparePatchConnect: retry $connectRetryCounter")
                                    medtrumService?.connect("Try reconnect from viewModel")
                                } else {
                                    aapsLogger.info(LTag.PUMP, "preparePatchConnect: failed to connect")
                                    updateSetupStep(SetupStep.ERROR)
                                }
                            }
                        }

                        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                            // Do nothing
                        }
                    }
                }
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumPatchViewModel pumpStateFlow: $state")
                if (_patchStep.value != null) {
                    when (state) {
                        MedtrumPumpState.NONE, MedtrumPumpState.IDLE         -> {
                            updateSetupStep(SetupStep.INITIAL)
                        }

                        MedtrumPumpState.FILLED                              -> {
                            updateSetupStep(SetupStep.FILLED)
                        }

                        MedtrumPumpState.PRIMING                             -> {
                            updateSetupStep(SetupStep.PRIMING)
                        }

                        MedtrumPumpState.PRIMED, MedtrumPumpState.EJECTED    -> {
                            updateSetupStep(SetupStep.PRIMED)
                        }

                        MedtrumPumpState.ACTIVE, MedtrumPumpState.ACTIVE_ALT -> {
                            updateSetupStep(SetupStep.ACTIVATED)
                        }

                        MedtrumPumpState.STOPPED                             -> {
                            updateSetupStep(SetupStep.STOPPED)
                        }

                        else                                                 -> {
                            updateSetupStep(SetupStep.ERROR)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun moveStep(newPatchStep: PatchStep) {
        oldPatchStep = _patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.CANCEL,
                PatchStep.COMPLETE,
                PatchStep.ACTIVATE_COMPLETE,
                PatchStep.START_DEACTIVATION,
                PatchStep.DEACTIVATE,
                PatchStep.FORCE_DEACTIVATION,
                PatchStep.DEACTIVATION_COMPLETE,
                PatchStep.PREPARE_PATCH,
                PatchStep.RETRY_ACTIVATION      -> {
                    // No connection required
                }

                PatchStep.RETRY_ACTIVATION_CONNECT,
                PatchStep.PREPARE_PATCH_CONNECT -> {
                    if (medtrumService?.isConnected == true) {
                        aapsLogger.info(LTag.PUMP, "moveStep: connected, not moving step")
                        return
                    }
                }

                PatchStep.PRIME,
                PatchStep.PRIMING,
                PatchStep.PRIME_COMPLETE,
                PatchStep.ATTACH_PATCH,
                PatchStep.ACTIVATE              -> {
                    if (medtrumService?.isConnected == false) {
                        aapsLogger.info(LTag.PUMP, "moveStep: not connected, not moving step")
                        return
                    }
                }
            }
        }

        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun forceMoveStep(newPatchStep: PatchStep) {
        val oldStep = _patchStep.value
        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "forceMoveStep: $oldStep -> $newPatchStep")
    }

    fun handleCancel() {
        if (oldPatchStep !in listOf(
                PatchStep.PREPARE_PATCH,
                PatchStep.START_DEACTIVATION,
                PatchStep.DEACTIVATE,
                PatchStep.FORCE_DEACTIVATION,
                PatchStep.DEACTIVATION_COMPLETE
            )
        ) {
            medtrumService?.disconnect("Cancel")
        }
        if (oldPatchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            scope.launch {
                while (medtrumService?.isConnecting == true || medtrumService?.isConnected == true) {
                    delay(100)
                }
                medtrumPump.pumpState = MedtrumPumpState.FILLED
                _events.tryEmit(PatchEvent.Finish)
            }
            return
        }
        _events.tryEmit(PatchEvent.Finish)
    }

    fun handleComplete() {
        medtrumService?.disconnect("Complete")
        _events.tryEmit(PatchEvent.Finish)
    }

    fun reset() {
        aapsLogger.info(LTag.PUMP, "reset: clearing state for new workflow session")
        _patchStep.value = null
        _setupStep.value = SetupStep.INITIAL
        _title.value = R.string.step_prepare_patch
        _wizardPage.value = WizardPage.PREPARE
        _totalSteps.value = 5
        _currentStepIndex.value = 0
        oldPatchStep = null
        mInitPatchStep = null
        connectRetryCounter = 0
    }

    fun initializePatchStep(step: PatchStep) {
        aapsLogger.info(LTag.PUMP, "initializePatchStep: $step")
        mInitPatchStep = prepareStep(step)
    }

    fun preparePatch() {
        medtrumService?.disconnect("PreparePatch")
    }

    fun preparePatchConnect() {
        scope.launch {
            if (medtrumService?.isConnected == false) {
                aapsLogger.info(LTag.PUMP, "preparePatch: new session")
                medtrumPump.patchSessionToken = Crypt().generateRandomToken()
                medtrumService?.connect("PreparePatch")
            } else {
                aapsLogger.error(LTag.PUMP, "preparePatch: Already connected when trying to prepare patch")
            }
        }
    }

    fun startPrime() {
        scope.launch {
            if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
                aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
            } else {
                if (medtrumService?.startPrime() == true) {
                    aapsLogger.info(LTag.PUMP, "startPrime: success!")
                } else {
                    aapsLogger.info(LTag.PUMP, "startPrime: failure!")
                    updateSetupStep(SetupStep.ERROR)
                }
            }
        }
    }

    fun startActivate() {
        scope.launch {
            if (medtrumService?.startActivate() == true) {
                aapsLogger.info(LTag.PUMP, "startActivate: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startActivate: failure!")
                updateSetupStep(SetupStep.ERROR)
            }
        }
    }

    fun deactivatePatch() {
        commandQueue.deactivate(object : Callback() {
            override fun run() {
                if (this.result.success) {
                    // State change will handle navigation
                } else {
                    if (medtrumPump.pumpState >= MedtrumPumpState.OCCLUSION && medtrumPump.pumpState <= MedtrumPumpState.NO_CALIBRATION) {
                        aapsLogger.info(LTag.PUMP, "deactivatePatch: force deactivation")
                        medtrumService?.disconnect("ForceDeactivation")
                        SystemClock.sleep(1000)
                        medtrumPump.pumpState = MedtrumPumpState.STOPPED
                    } else {
                        aapsLogger.info(LTag.PUMP, "deactivatePatch: failure!")
                        updateSetupStep(SetupStep.ERROR)
                    }
                }
            }
        })
    }

    fun retryActivationConnect() {
        scope.launch {
            if (medtrumService?.isConnected == false) {
                medtrumPump.pumpState = MedtrumPumpState.NONE
                medtrumService?.connect("RetryActivationConnect")
            } else {
                aapsLogger.error(LTag.PUMP, "retryActivationConnect: Already connected")
                updateSetupStep(SetupStep.ERROR)
            }
        }
    }

    private fun prepareStep(newStep: PatchStep): PatchStep {
        val stringResId = when (newStep) {
            PatchStep.PREPARE_PATCH            -> R.string.step_prepare_patch
            PatchStep.PREPARE_PATCH_CONNECT    -> R.string.step_prepare_patch_connect
            PatchStep.PRIME                    -> R.string.step_prime
            PatchStep.PRIMING                  -> R.string.step_priming
            PatchStep.PRIME_COMPLETE           -> R.string.step_priming_complete
            PatchStep.ATTACH_PATCH             -> R.string.step_attach
            PatchStep.ACTIVATE                 -> R.string.step_activate
            PatchStep.ACTIVATE_COMPLETE        -> R.string.step_activate_complete
            PatchStep.START_DEACTIVATION       -> R.string.step_deactivate
            PatchStep.DEACTIVATE               -> R.string.step_deactivating
            PatchStep.DEACTIVATION_COMPLETE    -> R.string.step_deactivate_complete
            PatchStep.RETRY_ACTIVATION,
            PatchStep.RETRY_ACTIVATION_CONNECT -> R.string.step_retry_activation

            PatchStep.COMPLETE,
            PatchStep.FORCE_DEACTIVATION,
            PatchStep.CANCEL                   -> _title.value
        }

        if (_title.value != stringResId) {
            aapsLogger.info(LTag.PUMP, "prepareStep: title: $stringResId")
            _title.value = stringResId
        }

        _patchStep.value = newStep
        updateWizardPage(newStep)

        // Handle immediate transitions (replicate MedtrumActivity behavior)
        when (newStep) {
            PatchStep.FORCE_DEACTIVATION -> {
                medtrumPump.pumpState = MedtrumPumpState.STOPPED
                moveStep(PatchStep.DEACTIVATION_COMPLETE)
            }

            PatchStep.CANCEL             -> handleCancel()
            PatchStep.COMPLETE           -> handleComplete()

            else                         -> { /* normal step, handled by UI */
            }
        }

        return newStep
    }

    fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${_setupStep.value}, newSetupStep: $newSetupStep")
        _setupStep.value = newSetupStep
    }

    private fun updateWizardPage(step: PatchStep) {
        val (page, total, current) = when (step) {
            // Activation flow: 5 steps
            PatchStep.PREPARE_PATCH,
            PatchStep.PREPARE_PATCH_CONNECT    -> Triple(WizardPage.PREPARE, 5, 0)

            PatchStep.PRIME,
            PatchStep.PRIMING,
            PatchStep.PRIME_COMPLETE           -> Triple(WizardPage.PRIME, 5, 1)

            PatchStep.ATTACH_PATCH             -> Triple(WizardPage.ATTACH, 5, 2)

            PatchStep.ACTIVATE,
            PatchStep.ACTIVATE_COMPLETE        -> Triple(WizardPage.ACTIVATE, 5, 3)

            PatchStep.COMPLETE                 -> Triple(WizardPage.COMPLETE, 5, 4)

            // Deactivation flow: 3 steps
            PatchStep.START_DEACTIVATION       -> Triple(WizardPage.CONFIRM_DEACTIVATE, 3, 0)

            PatchStep.DEACTIVATE,
            PatchStep.FORCE_DEACTIVATION       -> Triple(WizardPage.DEACTIVATING, 3, 1)

            PatchStep.DEACTIVATION_COMPLETE    -> Triple(WizardPage.DEACTIVATE_COMPLETE, 3, 2)

            // Retry activation
            PatchStep.RETRY_ACTIVATION,
            PatchStep.RETRY_ACTIVATION_CONNECT -> Triple(WizardPage.RETRY_ACTIVATION, 5, 0)

            PatchStep.CANCEL                   -> Triple(_wizardPage.value, _totalSteps.value, _currentStepIndex.value)
        }
        _wizardPage.value = page
        _totalSteps.value = total
        _currentStepIndex.value = current
    }

    enum class SetupStep {
        INITIAL, FILLED, PRIMING, PRIMED, ACTIVATED, ERROR, START_DEACTIVATION, STOPPED
    }
}
