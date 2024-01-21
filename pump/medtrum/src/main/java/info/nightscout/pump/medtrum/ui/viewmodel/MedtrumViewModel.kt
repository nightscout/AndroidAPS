package info.nightscout.pump.medtrum.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.encryption.Crypt
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MedtrumViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val medtrumPlugin: MedtrumPlugin,
    private val commandQueue: CommandQueue,
    val medtrumPump: MedtrumPump
) : BaseViewModel<MedtrumBaseNavigator>() {

    val patchStep = MutableLiveData<PatchStep>()

    val medtrumService: MedtrumService?
        get() = medtrumPlugin.getService()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _title = MutableLiveData<Int>(R.string.step_prepare_patch)
    val title: LiveData<Int>
        get() = _title

    private var oldPatchStep: PatchStep? = null
    private var mInitPatchStep: PatchStep? = null
    private var connectRetryCounter = 0

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                if (patchStep.value != null) {
                    when (state) {
                        ConnectionState.CONNECTED                                 -> {
                            medtrumPump.lastConnection = System.currentTimeMillis()
                        }

                        ConnectionState.DISCONNECTED                              -> {
                            if (patchStep.value in listOf(
                                    PatchStep.PRIME,
                                    PatchStep.PRIMING,
                                    PatchStep.PRIME_COMPLETE,
                                    PatchStep.ATTACH_PATCH,
                                    PatchStep.ACTIVATE
                                )
                            ) {
                                medtrumService?.connect("Try reconnect from viewModel")
                            }
                            if (patchStep.value in listOf(PatchStep.PREPARE_PATCH_CONNECT, PatchStep.RETRY_ACTIVATION_CONNECT)) {
                                // We are disconnected during prepare patch connect, this means we failed to connect (wrong session token?)
                                // Retry 3 times, then give up
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
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                if (patchStep.value != null) {
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
        oldPatchStep = patchStep.value

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
                    // Do nothing, these steps don't require a connection
                }

                PatchStep.RETRY_ACTIVATION_CONNECT,
                PatchStep.PREPARE_PATCH_CONNECT -> {
                    // Make sure we are disconnected, else don't move step
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
                    // Make sure we are connected, else don't move step
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
        val oldPatchStep = patchStep.value

        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "forceMoveStep: $oldPatchStep -> $newPatchStep")
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
            while (medtrumService?.isConnecting == true || medtrumService?.isConnected == true) {
                SystemClock.sleep(100)
            }
            // Set pump state to FILLED, so user will be able to retry activation again
            medtrumPump.pumpState = MedtrumPumpState.FILLED
        }
    }

    fun handleComplete() {
        medtrumService?.disconnect("Complete")
    }

    fun initializePatchStep(step: PatchStep) {
        mInitPatchStep = prepareStep(step)
    }

    fun preparePatch() {
        medtrumService?.disconnect("PreparePatch")
    }

    fun preparePatchConnect() {
        scope.launch {
            if (medtrumService?.isConnected == false) {
                aapsLogger.info(LTag.PUMP, "preparePatch: new session")
                // New session, generate new session token
                medtrumPump.patchSessionToken = Crypt().generateRandomToken()
                // Connect to pump
                medtrumService?.connect("PreparePatch")
            } else {
                aapsLogger.error(LTag.PUMP, "preparePatch: Already connected when trying to prepare patch")
                // Do nothing, we are already connected
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
                    // Do nothing, state change will handle this
                } else {
                    if (medtrumPump.pumpState >= MedtrumPumpState.OCCLUSION && medtrumPump.pumpState <= MedtrumPumpState.NO_CALIBRATION) {
                        // We are in a fault state, we need to force deactivation
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
                // Reset medtrum pump state, we will pickup pomp state on connect
                medtrumPump.pumpState = MedtrumPumpState.NONE
                medtrumService?.connect("RetryActivationConnect")
            } else {
                aapsLogger.error(LTag.PUMP, "retryActivationConnect: Already connected when trying to prepare patch")
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

        val currentTitle = _title.value
        aapsLogger.info(LTag.PUMP, "prepareStep: title before cond: $stringResId")
        if (currentTitle != stringResId) {
            aapsLogger.info(LTag.PUMP, "prepareStep: title: $stringResId")
            stringResId?.let { _title.postValue(it) }
        }

        patchStep.postValue(newStep)

        return newStep
    }

    enum class SetupStep { INITIAL, FILLED, PRIMING, PRIMED, ACTIVATED, ERROR, START_DEACTIVATION, STOPPED
    }

    val setupStep = MutableLiveData<SetupStep>()

    fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${setupStep.value}, newSetupStep: $newSetupStep")
        setupStep.postValue(newSetupStep)
    }
}
