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
import info.nightscout.interfaces.queue.CommandQueue
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
    private val commandQueue: CommandQueue,
    val medtrumPump: MedtrumPump
) : BaseViewModel<MedtrumBaseNavigator>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _canDoRefresh = SingleLiveEvent<Boolean>()
    val canDoRefresh: LiveData<Boolean>
        get() = _canDoRefresh

    private val _canDoResetAlarms = SingleLiveEvent<Boolean>()
    val canDoResetAlarms: LiveData<Boolean>
        get() = _canDoResetAlarms

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    private val _lastConnected = SingleLiveEvent<String>()
    val lastConnected: LiveData<String>
        get() = _lastConnected

    private val _activeAlarms = SingleLiveEvent<String>()
    val activeAlarms: LiveData<String>
        get() = _activeAlarms

    private val _pumpType = SingleLiveEvent<String>()
    val pumpType: LiveData<String>
        get() = _pumpType

    private val _fwVersion = SingleLiveEvent<String>()
    val fwVersion: LiveData<String>
        get() = _fwVersion
    
    private val _patchNo = SingleLiveEvent<String>()
    val patchNo: LiveData<String>
        get() = _patchNo

    private val _patchExpiry = SingleLiveEvent<String>()
    val patchExpiry: LiveData<String>
        get() = _patchExpiry

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                when (state) {
                    ConnectionState.CONNECTING   -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.CONNECTED    -> {
                        _bleStatus.postValue("{fa-bluetooth}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.DISCONNECTED -> {
                        _bleStatus.postValue("{fa-bluetooth-b}")
                        if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                            _canDoRefresh.postValue(true)
                        } else {
                            _canDoRefresh.postValue(false)
                        }
                    }
                }
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                    _canDoResetAlarms.postValue(true)
                } else {
                    _canDoResetAlarms.postValue(false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun onClickRefresh() {
        commandQueue.readStatus(rh.gs(R.string.requested_by_user), null)
    }

    fun onClickResetAlarms() {
        commandQueue.clearAlarms(null)
    }

    fun onClickChangePatch() {
        aapsLogger.debug(LTag.PUMP, "ChangePatch Patch clicked!")
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else {
            _eventHandler.postValue(UIEvent(EventType.CHANGE_PATCH_CLICKED))
        }
    }
}
 