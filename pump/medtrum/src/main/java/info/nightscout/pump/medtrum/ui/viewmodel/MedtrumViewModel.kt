package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.encryption.Crypt
import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import info.nightscout.pump.medtrum.ui.event.SingleLiveEvent
import info.nightscout.pump.medtrum.ui.event.UIEvent
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MedtrumViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val medtrumPlugin: MedtrumPlugin,
    private val medtrumPump: MedtrumPump,
    private val sp: SP
) : BaseViewModel<MedtrumBaseNavigator>() {

    val patchStep = MutableLiveData<PatchStep>()

    val medtrumService: MedtrumService?
        get() = medtrumPlugin.getService()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _title = MutableLiveData<Int>(R.string.step_prepare_patch)
    val title: LiveData<Int>
        get() = _title

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private var mInitPatchStep: PatchStep? = null

    init {
        // TODO destroy scope
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                if (patchStep.value != null) {
                    when (state) {
                        ConnectionState.CONNECTED    -> {
                            if (patchStep.value == PatchStep.START_DEACTIVATION) {
                                updateSetupStep(SetupStep.READY_DEACTIVATE)
                            }
                            medtrumPump.lastConnection = System.currentTimeMillis()
                        }

                        ConnectionState.DISCONNECTED -> {
                            if (patchStep.value != PatchStep.DEACTIVATION_COMPLETE && patchStep.value != PatchStep.COMPLETE && patchStep.value != PatchStep.CANCEL) {
                                medtrumService?.connect("Try reconnect from viewModel")
                            }
                        }

                        ConnectionState.CONNECTING   -> {
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
                            // updateSetupStep(SetupStep.PRIMING)
                            // TODO: What to do here? start prime counter?
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
        val oldPatchStep = patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.CANCEL                -> {
                    if (medtrumService?.isConnected == true || medtrumService?.isConnecting == true) medtrumService?.disconnect("Cancel") else {
                    }
                    // TODO: For DEACTIVATE STATE we might want to move to force cancel screen
                    if (oldPatchStep == PatchStep.START_DEACTIVATION || oldPatchStep == PatchStep.DEACTIVATE) {
                        // What to do here?
                    }
                }

                PatchStep.COMPLETE              -> {
                    if (medtrumService?.isConnected == true || medtrumService?.isConnecting == true) medtrumService?.disconnect("Complete") else {
                    }
                }

                PatchStep.DEACTIVATION_COMPLETE -> {
                    if (medtrumService?.isConnected == true || medtrumService?.isConnecting == true) medtrumService?.disconnect("DeactivationComplete") else {
                    }
                }

                else                            -> {
                    // Make sure we are connected, else dont move step
                    if (medtrumService?.isConnected == false) {
                        aapsLogger.info(LTag.PUMP, "moveStep: not connected, not moving step")
                        return
                    } else {                     
                    }
                }
            }
        }

        prepareStep(newPatchStep)

        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun initializePatchStep(step: PatchStep?) {
        mInitPatchStep = prepareStep(step)
    }

    fun preparePatch() {
        // Make sure patch step is updated when already filled
        // TODO: Maybe a nicer solution for this
        if (medtrumPump.pumpState == MedtrumPumpState.FILLED) {
            updateSetupStep(SetupStep.FILLED)
        }
        // New session, generate new session token, only do this when not connected
        if (medtrumService?.isConnected == false) {
            aapsLogger.info(LTag.PUMP, "preparePatch: new session")
            medtrumPump.patchSessionToken = Crypt().generateRandomToken()        
            // Connect to pump
            medtrumService?.connect("PreparePatch")
        } else {
            aapsLogger.error(LTag.PUMP, "preparePatch: Already connected when trying to prepare patch")
            // Do nothing here, continue with old key and connection
        }
    }

    fun startPrime() {
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

    fun startActivate() {
        if (medtrumService?.startActivate() == true) {
            aapsLogger.info(LTag.PUMP, "startActivate: success!")
        } else {
            aapsLogger.info(LTag.PUMP, "startActivate: failure!")
            updateSetupStep(SetupStep.ERROR)
        }
    }

    fun startDeactivation() {
        // Start connecting if needed
        if (medtrumService?.isConnected == true) {
            updateSetupStep(SetupStep.READY_DEACTIVATE)
        } else if (medtrumService?.isConnecting != true) {
            medtrumService?.connect("StartDeactivation")
        }
        // TODO: Also start timer to check if connection is made, if timed out show force forget patch
    }

    fun deactivatePatch() {
        if (medtrumService?.deactivatePatch() == true) {
            aapsLogger.info(LTag.PUMP, "deactivatePatch: success!")
        } else {
            aapsLogger.info(LTag.PUMP, "deactivatePatch: failure!")
            // TODO: State to force forget the patch or try again
            updateSetupStep(SetupStep.ERROR)
        }
    }

    private fun prepareStep(step: PatchStep?): PatchStep {
        (step ?: convertToPatchStep(medtrumPump.pumpState)).let { newStep ->
            when (newStep) {
                PatchStep.PREPARE_PATCH                            -> R.string.step_prepare_patch
                PatchStep.PRIME                                    -> R.string.step_prime
                PatchStep.ATTACH_PATCH                             -> R.string.step_attach
                PatchStep.ACTIVATE                                 -> R.string.step_activate
                PatchStep.COMPLETE                                 -> R.string.step_complete
                PatchStep.START_DEACTIVATION, PatchStep.DEACTIVATE -> R.string.step_deactivate
                PatchStep.DEACTIVATION_COMPLETE                    -> R.string.step_complete
                else                                               -> _title.value
            }.let {
                aapsLogger.info(LTag.PUMP, "prepareStep: title before cond: $it")
                if (_title.value != it) {
                    aapsLogger.info(LTag.PUMP, "prepareStep: title: $it")
                    _title.postValue(it)
                }
            }

            patchStep.postValue(newStep)

            return newStep
        }
    }

    enum class SetupStep { INITIAL, FILLED, PRIMED, ACTIVATED, ERROR, START_DEACTIVATION, STOPPED, READY_DEACTIVATE
    }

    val setupStep = MutableLiveData<SetupStep>()

    private fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${setupStep.value}, newSetupStep: $newSetupStep")
        setupStep.postValue(newSetupStep)
    }
}
