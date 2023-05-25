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
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.R
import info.nightscout.pump.medtrum.code.ConnectionState
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val profileFunction: ProfileFunction,
    private val medtrumPump: MedtrumPump
) : BaseViewModel<MedtrumBaseNavigator>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    private val _isPatchActivated = SingleLiveEvent<Boolean>()
    val isPatchActivated: LiveData<Boolean>
        get() = _isPatchActivated

    private val _pumpState = SingleLiveEvent<String>()
    val pumpState: LiveData<String>
        get() = _pumpState

    private val _basalType = SingleLiveEvent<String>()
    val basalType: LiveData<String>
        get() = _basalType

    private val _runningBasalRate = SingleLiveEvent<String>()
    val runningBasalRate: LiveData<String>
        get() = _runningBasalRate

    private val _reservoir = SingleLiveEvent<String>()
    val reservoir: LiveData<String>
        get() = _reservoir

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                when (state) {
                    ConnectionState.CONNECTING   -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                    }

                    ConnectionState.CONNECTED    -> {
                        _bleStatus.postValue("{fa-bluetooth}")
                    }

                    ConnectionState.DISCONNECTED -> {
                        _bleStatus.postValue("{fa-bluetooth-b}")
                    }
                }
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                if (state > MedtrumPumpState.EJECTED && state < MedtrumPumpState.STOPPED) {
                    _isPatchActivated.postValue(true)
                } else {
                    _isPatchActivated.postValue(false)
                }
            }
        }
        scope.launch {
            medtrumPump.lastBasalRateFlow.collect { rate ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel runningBasalRateFlow: $rate")
                _runningBasalRate.postValue(String.format(rh.gs(R.string.current_basal_rate), rate))
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                _pumpState.postValue(state.toString())
            }
        }
        scope.launch {
            medtrumPump.reservoirFlow.collect { reservoir ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel reservoirFlow: $reservoir")
                _reservoir.postValue(String.format(rh.gs(R.string.reservoir_level), reservoir))
            }
        }
        scope.launch {
            medtrumPump.lastBasalTypeFlow.collect { basalType ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel basalTypeFlow: $basalType")
                _basalType.postValue(basalType.toString())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
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
        _eventHandler.postValue(UIEvent(EventType.DEACTIVATION_CLICKED))
    }
}