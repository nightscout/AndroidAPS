package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.code.PatchStep
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
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
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                when (state) {
                    MedtrumPumpState.NONE, MedtrumPumpState.IDLE         -> {
                        setupStep.postValue(SetupStep.INITIAL)
                    }

                    MedtrumPumpState.FILLED                              -> {
                        setupStep.postValue(SetupStep.FILLED)
                    }

                    MedtrumPumpState.PRIMING                             -> {
                        // setupStep.postValue(SetupStep.PRIMING)
                        // TODO: What to do here? start prime counter?
                    }

                    MedtrumPumpState.PRIMED, MedtrumPumpState.EJECTED    -> {
                        setupStep.postValue(SetupStep.PRIMED)
                    }

                    MedtrumPumpState.ACTIVE, MedtrumPumpState.ACTIVE_ALT -> {
                        setupStep.postValue(SetupStep.ACTIVATED)
                    }

                    else                                                 -> {
                        setupStep.postValue(SetupStep.ERROR)
                    }
                }
            }
        }
    }

    fun moveStep(newPatchStep: PatchStep) {
        val oldPatchStep = patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.CANCEL -> {
                    // if (medtrumService?.isConnected == true || medtrumService?.isConnecting == true) medtrumService?.disconnect("Cancel") else {
                    // }
                }

                else             -> null
            }?.let {
                // TODO Update lifecycle
            }
        }

        prepareStep(newPatchStep)

        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun initializePatchStep(step: PatchStep?) {
        mInitPatchStep = prepareStep(step)
    }

    fun preparePatch() {
        // TODO When we dont need to connect what needs to be done here?
        medtrumService?.connect("PreparePatch")
    }

    fun startPrime() {
        // TODO: Get result from service
        if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
            aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
        } else {
            if (medtrumService?.startPrime() == true) {
                aapsLogger.info(LTag.PUMP, "startPrime: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startPrime: failure!")
                setupStep.postValue(SetupStep.ERROR)
            }
        }
    }

    fun startActivate() {
        if (medtrumService?.startActivate() == true) {
            aapsLogger.info(LTag.PUMP, "startActivate: success!")
        } else {
            aapsLogger.info(LTag.PUMP, "startActivate: failure!")
            setupStep.postValue(SetupStep.ERROR)
        }
    }

    private fun prepareStep(step: PatchStep?): PatchStep {
        // TODO Title per screen :) And proper sync with patchstate
        (step ?: convertToPatchStep(medtrumPump.pumpState)).let { newStep ->
            when (newStep) {
                PatchStep.PREPARE_PATCH -> R.string.step_prepare_patch
                PatchStep.PRIME         -> R.string.step_prime
                PatchStep.ATTACH_PATCH  -> R.string.step_attach
                PatchStep.ACTIVATE      -> R.string.step_activate
                PatchStep.COMPLETE      -> R.string.step_complete
                else                    -> _title.value
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

    enum class SetupStep {
        INITIAL,
        FILLED,
        PRIMED,
        ACTIVATED,
        ERROR
    }

    val setupStep = MutableLiveData<SetupStep>()

    private fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${setupStep.value}, newSetupStep: $newSetupStep")
        setupStep.postValue(newSetupStep)
    }
}
