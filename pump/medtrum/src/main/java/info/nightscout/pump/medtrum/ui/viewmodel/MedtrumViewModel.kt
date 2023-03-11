package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.pump.medtrum.MedtrumPlugin
import info.nightscout.pump.medtrum.services.MedtrumService
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.code.PatchStep
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
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class MedtrumViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val medtrumPlugin: MedtrumPlugin,
    private val sp: SP
) : BaseViewModel<MedtrumBaseNavigator>() {

    val patchStep = MutableLiveData<PatchStep>()

    val title = "Activation"

    val medtrumService: MedtrumService?
        get() = medtrumPlugin.getService()

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private var mInitPatchStep: PatchStep? = null

    init {
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           when (it.status) {
                               EventPumpStatusChanged.Status.CONNECTING   -> {}

                               EventPumpStatusChanged.Status.CONNECTED
                                                                          -> if (patchStep.value == PatchStep.PREPARE_PATCH) setupStep.postValue(SetupStep.CONNECTED) else {
                               }

                               EventPumpStatusChanged.Status.DISCONNECTED -> {}

                               else                                       -> {}
                           }
                       }, fabricPrivacy::logException)
    }

    fun moveStep(newPatchStep: PatchStep) {
        val oldPatchStep = patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.CANCEL -> {
                    if (medtrumService?.isConnected == true || medtrumService?.isConnecting == true) medtrumService?.disconnect("Cancel") else {
                    }
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
        // TODO: Decide if we want to connect already when user is still filling, or if we want to wait after user is done filling
        medtrumService?.connect("PreparePatch")
    }

    private fun prepareStep(step: PatchStep?): PatchStep {
        // TODO Title per screen :) And proper sync with patchstate
        // (step ?: convertToPatchStep(patchConfig.lifecycleEvent.lifeCycle)).let { newStep ->

        (step ?: PatchStep.SAFE_DEACTIVATION).let { newStep ->
            when (newStep) {

                else -> ""
            }.let {

            }

            patchStep.postValue(newStep)

            return newStep
        }
    }

    enum class SetupStep {
        CONNECTED,
        PRIME_READY,
        ACTIVATED
    }

    val setupStep = MutableLiveData<SetupStep>()

    private fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${setupStep.value}, newSetupStep: $newSetupStep")
        setupStep.postValue(newSetupStep)
    }
}
