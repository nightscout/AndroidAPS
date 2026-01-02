package app.aaps.pump.medtrum.ui.viewmodel

import androidx.lifecycle.LiveData
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.code.EventType
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.comm.enums.ModelType
import app.aaps.pump.medtrum.ui.MedtrumBaseNavigator
import app.aaps.pump.medtrum.ui.event.SingleLiveEvent
import app.aaps.pump.medtrum.ui.event.UIEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val medtrumPlugin: MedtrumPlugin,
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

    private val _lastConnectionMinAgo = SingleLiveEvent<String>()
    val lastConnectionMinAgo: LiveData<String>
        get() = _lastConnectionMinAgo

    private val _lastBolus = SingleLiveEvent<String>()
    val lastBolus: LiveData<String>
        get() = _lastBolus

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

    private val _patchAge = SingleLiveEvent<String>()
    val patchAge: LiveData<String>
        get() = _patchAge

    private val _activeBolusStatus = SingleLiveEvent<String>()
    val activeBolusStatus: LiveData<String>
        get() = _activeBolusStatus

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel connectionStateFlow: $state")
                when (state) {
                    ConnectionState.CONNECTING    -> {
                        _bleStatus.postValue("{fa-bluetooth-b spin}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.CONNECTED     -> {
                        _bleStatus.postValue("{fa-bluetooth}")
                        _canDoRefresh.postValue(false)
                    }

                    ConnectionState.DISCONNECTED  -> {
                        _bleStatus.postValue("{fa-bluetooth-b}")
                        if (medtrumPump.pumpState > MedtrumPumpState.EJECTED && medtrumPump.pumpState < MedtrumPumpState.STOPPED) {
                            _canDoRefresh.postValue(true)
                        } else {
                            _canDoRefresh.postValue(false)
                        }
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
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel pumpStateFlow: $state")
                _canDoResetAlarms.postValue(
                    medtrumPump.pumpState.isSuspendedByPump()
                )

                updateGUI()
            }
        }
        scope.launch {
            medtrumPump.bolusAmountDeliveredFlow.collect { bolusAmount ->
                aapsLogger.debug(LTag.PUMP, "MedtrumViewModel bolusAmountDeliveredFlow: $bolusAmount")
                if (!medtrumPump.bolusDone && medtrumPlugin.isInitialized()) {
                    _activeBolusStatus.postValue(
                        dateUtil.timeString(medtrumPump.bolusStartTime) + " " + dateUtil.sinceString(medtrumPump.bolusStartTime, rh)
                            + " " + rh.gs(app.aaps.core.ui.R.string.format_insulin_units, bolusAmount) + " / " + rh.gs(
                            app.aaps.core.ui.R.string.format_insulin_units, medtrumPump.bolusAmountToBeDelivered
                        )
                    )
                }
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

    fun onClickResetAlarms() {
        commandQueue.clearAlarms(null)
    }

    fun onClickChangePatch() {
        aapsLogger.debug(LTag.PUMP, "ChangePatch Patch clicked!")
        val profile = profileFunction.getProfile()
        if (profile == null) {
            _eventHandler.postValue(UIEvent(EventType.PROFILE_NOT_SET))
        } else if (medtrumPump.pumpSN == 0L) {
            _eventHandler.postValue(UIEvent(EventType.SERIAL_NOT_SET))
        } else {
            _eventHandler.postValue(UIEvent(EventType.CHANGE_PATCH_CLICKED))
        }
    }

    private fun updateGUI() {
        // Update less dynamic values
        if (medtrumPump.lastConnection != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - medtrumPump.lastConnection
            val agoMinutes = agoMilliseconds / 1000 / 60
            _lastConnectionMinAgo.postValue(rh.gs(app.aaps.core.interfaces.R.string.minago, agoMinutes))
        }
        if (medtrumPump.lastBolusTime != 0L) {
            val agoMilliseconds = System.currentTimeMillis() - medtrumPump.lastBolusTime
            val agoHours = agoMilliseconds.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                _lastBolus.postValue(
                    dateUtil.timeString(medtrumPump.lastBolusTime) + " " + dateUtil.sinceString(medtrumPump.lastBolusTime, rh) + " " + rh.gs(
                        app.aaps.core.ui.R.string.format_insulin_units, medtrumPump.lastBolusAmount
                    )
                )
            else _lastBolus.postValue("")
        }
        if (medtrumPump.bolusDone || !medtrumPlugin.isInitialized()) {
            _activeBolusStatus.postValue("")
        }

        val activeAlarmStrings = medtrumPump.activeAlarms.map { medtrumPump.alarmStateToString(it) }
        _activeAlarms.postValue(activeAlarmStrings.joinToString("\n"))
        _pumpType.postValue(ModelType.fromValue(medtrumPump.deviceType).toString())
        _fwVersion.postValue(medtrumPump.swVersion)
        _patchNo.postValue(medtrumPump.patchId.toString())

        // Pump age
        if (medtrumPump.patchStartTime == 0L) {
            _patchAge.postValue("")
        } else {
            val age = System.currentTimeMillis() - medtrumPump.patchStartTime
            val agoString = dateUtil.timeAgoFullString(age, rh)
            val ageString = dateUtil.dateAndTimeString(medtrumPump.patchStartTime) + "\n" + agoString

            _patchAge.postValue(ageString)
        }

        // Pump Expiration
        if (medtrumPump.desiredPatchExpiration) {
            if (medtrumPump.patchStartTime == 0L) {
                _patchExpiry.postValue("")
            } else {
                val expiry = medtrumPump.patchStartTime + T.hours(72).msecs()
                val currentTime = System.currentTimeMillis()
                val timeLeft = expiry - currentTime
                val daysLeft = T.msecs(timeLeft).days()
                val hoursLeft = T.msecs(timeLeft).hours() % 24

                val daysString = if (daysLeft > 0) "$daysLeft ${rh.gs(app.aaps.core.interfaces.R.string.days)} " else ""
                val hoursString = "$hoursLeft ${rh.gs(app.aaps.core.interfaces.R.string.hours)}"

                val expiryString = dateUtil.dateAndTimeString(expiry) + "\n(" + daysString + hoursString + ")"

                _patchExpiry.postValue(expiryString)
            }
        } else {
            _patchExpiry.postValue(rh.gs(R.string.expiry_not_enabled))
        }
    }
}
