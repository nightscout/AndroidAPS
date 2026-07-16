package app.aaps.pump.aaruy.ui.viewmodel

import androidx.lifecycle.LiveData
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.code.EventType
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import app.aaps.pump.aaruy.ui.AaruyBaseNavigator
import app.aaps.pump.aaruy.ui.event.SingleLiveEvent
import app.aaps.pump.aaruy.ui.event.UIEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class AaruyOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val aaruyPlugin: AaruyPlugin,
    private val preferences: Preferences,
    val aaruyPump: AaruyPump
) : BaseViewModel<AaruyBaseNavigator>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    val aaruyService: AaruyService?
        get() = aaruyPlugin.getService()

    private val _eventHandler = SingleLiveEvent<UIEvent<EventType>>()
    val eventHandler: LiveData<UIEvent<EventType>>
        get() = _eventHandler

    private val _canDoRefresh = SingleLiveEvent<Boolean>()
    val canDoRefresh: LiveData<Boolean>
        get() = _canDoRefresh

    private val _canDoReconnect = SingleLiveEvent<Boolean>()
    val canDoReconnect: LiveData<Boolean>
        get() = _canDoReconnect

    private val _canDoResetAlarms = SingleLiveEvent<Boolean>()
    val canDoResetAlarms: LiveData<Boolean>
        get() = _canDoResetAlarms

    private val _bleStatus = SingleLiveEvent<String>()
    val bleStatus: LiveData<String>
        get() = _bleStatus

    private val _lastConnectionMinAgo = SingleLiveEvent<String>()
    val lastConnectionMinAgo: LiveData<String>
        get() = _lastConnectionMinAgo

    private val _lastBolus = SingleLiveEvent<String>()
    val lastBolus: LiveData<String>
        get() = _lastBolus

    private val _activeAlarms = SingleLiveEvent<String>()
    val activeAlarms: LiveData<String>
        get() = _activeAlarms

    private val _fwVersion = SingleLiveEvent<String>()
    val fwVersion: LiveData<String>
        get() = _fwVersion

    private val _pumpType = SingleLiveEvent<String>()
    val pumpType: LiveData<String>
        get() = _pumpType

    private val _pumpSerial = SingleLiveEvent<String>()
    val pumpSerial: LiveData<String>
        get() = _pumpSerial

    private val _activeBolusStatus = SingleLiveEvent<String>()
    val activeBolusStatus: LiveData<String>
        get() = _activeBolusStatus

    private val _basalRate = SingleLiveEvent<String>()
    val basalRate: LiveData<String>
        get() = _basalRate

    private val _remainReservoir = SingleLiveEvent<String>()
    val remainReservoir: LiveData<String>
        get() = _remainReservoir

    private val _remainBattery = SingleLiveEvent<String>()
    val remainBattery: LiveData<String>
        get() = _remainBattery

    init {
        scope.launch {
            aaruyPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel connectionStateFlow: $state")
                if (state == ConnectionState.CONNECTED) {
                    _canDoReconnect.postValue(false)
                }
                else {
                    if (preferences.get(AaruyStringKey.MacAddress) != "") {
                        _canDoReconnect.postValue(true)
                    }
                    else {
                        _canDoReconnect.postValue(false)
                    }
                }

                when (state) {
                    ConnectionState.CONNECTING    -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.CONNECTED     -> {
                        _bleStatus.postValue("{fa-bluetooth}")
                        aaruyService?.getPumpVersionNum()
                        if (aaruyPump.pumpState.isCanDoRefresh()) {
                            _canDoRefresh.postValue(true)
                            aaruyService?.readPumpStatus()

                        }
                        else {
                            _canDoRefresh.postValue(false)
                        }
                    }

                    ConnectionState.DISCONNECTED  -> {
                        _bleStatus.postValue("{fa-bluetooth-b}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.DISCONNECTING -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(true)
                    }
                }
                updateGUI()
            }
        }
        scope.launch {
            aaruyPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel pumpStateFlow: $state")
                _canDoResetAlarms.postValue(
                    aaruyPump.pumpState.isSuspendedByPump()
                )
                if (state.isCanDoRefresh() && aaruyPump.connectionState == ConnectionState.CONNECTED) {
                    _canDoRefresh.postValue(true)
                }
                else {
                    _canDoRefresh.postValue(false)
                }

                updateGUI()
            }
        }
        scope.launch {
            aaruyPump.pumpWarningFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel pumpStateFlow: $state")
                _canDoResetAlarms.postValue(
                    (AlarmState.isHighAlarm(state) || AlarmState.isLowAlarm(state))
                )
                updateGUI()
            }
        }
        scope.launch {
            aaruyPump.bolusAmountDeliveredFlow.collect { bolusAmount ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel bolusAmountDeliveredFlow: $bolusAmount")
                if (!aaruyPump.bolusDone && aaruyPlugin.isInitialized()) {
                    _activeBolusStatus.postValue(
                        dateUtil.timeString(aaruyPump.bolusStartTime) + " " + dateUtil.sinceString(aaruyPump.bolusStartTime, rh)
                            + " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusAmount) + " / " + rh.gs(
                            app.aaps.core.ui.R.string.format_insulin_units, aaruyPump.bolusAmountToBeDelivered
                        )
                    )
                }
            }
        }
        scope.launch {
            aaruyPump.lastBasalRateFlow.collect { state ->
                _basalRate.postValue(String.format("%.3f U/h", state))

                updateGUI()
            }
        }
        scope.launch {
            aaruyPump.reservoirFlow.collect { state ->
                _remainReservoir.postValue(String.format("%.0f U", state))

                updateGUI()
            }
        }
        scope.launch {
            aaruyPump.remainBatteryFlow.collect { state ->
                _remainBattery.postValue(String.format("%.0f %%", state))

                updateGUI()
            }
        }
        // Periodically update gui
        scope.launch {
            while (true) {
                updateGUI()
                kotlinx.coroutines.delay(T.mins(1).msecs())
            }
        }
        // Update gui on init
        updateGUI()
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun onClickRefresh() {
        commandQueue.readStatus(rh.gs(R.string.requested_by_user), null)
    }

    fun onClickReconnect() {
        if (preferences.get(AaruyStringKey.MacAddress) != "") {
            aaruyService?.connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
        }
    }

    fun onClickResetAlarms() {
        aaruyPump.clearAlarmState()
        commandQueue.clearAlarms(null)
        _canDoResetAlarms.postValue(false)
    }

    fun onClickReplaceResvBat() {
        aapsLogger.debug(LTag.PUMP, "replace resv/bat clicked!")
        val profile = profileFunction.getProfile()

        // _eventHandler.postValue(UIEvent(EventType.CHANGE_REPLACE_CLICKED))

        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else if (aaruyPump.pumpSN.isEmpty()) {
            _eventHandler.postValue(UIEvent(EventType.SERIAL_NOT_SET))
        } else {
            _eventHandler.postValue(UIEvent(EventType.CHANGE_REPLACE_CLICKED))
        }
    }

    fun onClickViewHistory() {
        aaruyService?.requestHistoryData()
        aapsLogger.debug(LTag.PUMP, "view history clicked!")
        _eventHandler.postValue(UIEvent(EventType.VIEW_HISTORY_CLICKED))
    }

    private fun updateGUI() {
        // Update less dynamic values
        if (aaruyPump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - aaruyPump.lastConnection
            val agoMinutes = agoMilliseconds / 1000 / 60
            _lastConnectionMinAgo.postValue(rh.gs(app.aaps.core.interfaces.R.string.minago, agoMinutes))
        }
        if (aaruyPump.lastBolusTime != 0L) {
            _lastBolus.postValue(
                dateUtil.timeString(aaruyPump.lastBolusTime) + " " + dateUtil.sinceString(aaruyPump.lastBolusTime, rh) + " " + rh.gs(
                    app.aaps.core.ui.R.string.format_insulin_units, aaruyPump.lastBolusAmount
                )
            )
        }
        if (aaruyPump.bolusDone || !aaruyPlugin.isInitialized()) {
            _activeBolusStatus.postValue("")
        }

        val activeAlarmStrings = aaruyPump.activeAlarms.map { aaruyPump.alarmStateToString(it) }
        _activeAlarms.postValue(activeAlarmStrings.joinToString("\n"))
        _fwVersion.postValue(aaruyPump.swVersion)
        if (aaruyPump.pumpSN.length > 6)
            _pumpSerial.postValue(aaruyPump.pumpSN.substring(6))
        else {
            _pumpSerial.postValue(aaruyPump.pumpSN)
        }

        when (AaruyType.fromValue(aaruyPump.pumpType)) {
            AaruyType.B200A-> _pumpType.postValue("AR-B200A")
            AaruyType.B200B -> _pumpType.postValue("AR-B200B")
            AaruyType.B200C -> _pumpType.postValue("AR-B200C")
            AaruyType.B200D -> _pumpType.postValue("AR-B200D")
            AaruyType.INVALID -> _pumpType.postValue("AR-UNKOWN")
        }
    }
}
