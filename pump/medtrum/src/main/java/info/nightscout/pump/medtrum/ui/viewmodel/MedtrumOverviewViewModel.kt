package info.nightscout.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.pump.medtrum.code.EventType
import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import info.nightscout.pump.medtrum.ui.event.SingleLiveEvent
import info.nightscout.pump.medtrum.ui.event.UIEvent
import info.nightscout.pump.medtrum.ui.viewmodel.BaseViewModel
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val profileFunction: ProfileFunction,
) : BaseViewModel<MedtrumBaseNavigator>() {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    // TODO make these livedata
    val isPatchActivated: Boolean
        get() = false // TODO

    init {
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           _bleStatus.value = when (it.status) {
                               EventPumpStatusChanged.Status.CONNECTING   ->
                                   "{fa-bluetooth-b spin} ${it.secondsElapsed}s"

                               EventPumpStatusChanged.Status.CONNECTED    ->
                                   "{fa-bluetooth}"

                               EventPumpStatusChanged.Status.DISCONNECTED ->
                                   "{fa-bluetooth-b}"

                               else                                       ->
                                   ""
                           }
                       }, fabricPrivacy::logException)
    }

    fun onClickActivation() {
        aapsLogger.debug(LTag.PUMP, "Start Patch clicked!")
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else {
            _eventHandler.postValue(UIEvent(EventType.ACTIVATION_CLICKED))
        }
    }

    fun onClickDeactivation() {
        aapsLogger.debug(LTag.PUMP, "Stop Patch clicked!")
        // TODO
    }
}