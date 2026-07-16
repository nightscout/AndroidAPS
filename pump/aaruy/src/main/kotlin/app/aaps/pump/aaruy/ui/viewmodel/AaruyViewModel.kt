package app.aaps.pump.aaruy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPlugin
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.code.ReplaceStep
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyService
import app.aaps.pump.aaruy.ui.AaruyBaseNavigator
import app.aaps.pump.aaruy.ui.event.SingleLiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class AaruyViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aaruyPlugin: AaruyPlugin,
    private val commandQueue: CommandQueue,
    private val preferences: Preferences,
    val aaruyPump: AaruyPump
) : BaseViewModel<AaruyBaseNavigator>() {

    val replaceStep = MutableLiveData<ReplaceStep>()
    val currentStatus = MutableLiveData<AaruyPumpState>()
    val currentErrCode = MutableLiveData<Int>()

    val aaruyService: AaruyService?
        get() = aaruyPlugin.getService()

    private val _canDoUnbind = SingleLiveEvent<Boolean>()
    val canDoUnbind: LiveData<Boolean>
        get() = _canDoUnbind

    private val _canSendCmd = SingleLiveEvent<Boolean>()
    val canSendCmd: LiveData<Boolean>
        get() = _canSendCmd

    private var mInitReplaceStep: ReplaceStep? = null


    private val scope = CoroutineScope(Dispatchers.Default)
    
    init {
        scope.launch {
            aaruyPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "AaruyViewModel connectionStateFlow: $state")
                _canSendCmd.postValue(false)
                when (state) {
                    ConnectionState.CONNECTING -> {

                    }

                    ConnectionState.CONNECTED     -> {
                        _canSendCmd.postValue(true)
                    }

                    ConnectionState.DISCONNECTED  -> {

                    }

                    ConnectionState.DISCONNECTING -> {

                    }
                }
                _canDoUnbind.postValue(preferences.get(AaruyStringKey.PumpBoundAddress) != "")
            }
        }
        scope.launch {
            aaruyPump.pumpStateFlow.collect { state ->
                currentStatus.postValue(state)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun initializePatchStep(step: ReplaceStep) {
        mInitReplaceStep = prepareStep(step)
    }

    fun replaceDevice() {
        scope.launch {
            if (aaruyService?.replaceDevice() == true) {
                aapsLogger.info(LTag.PUMP, "replaceDevice: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "replaceDevice: failure!")
            }
        }
    }

    fun pushForward() {
        scope.launch {
            if (aaruyService?.pushForward() == true) {
                aapsLogger.info(LTag.PUMP, "pushForward: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "pushForward: failure!")
            }
        }
    }

    fun sendNeedleType(needType:Int) {
        scope.launch {
            aaruyPump.softNeedleType = needType
            aaruyService?.aaruyBle?.syncAndLoadBasicParam()
        }
    }

    fun needlePushAir() {
        scope.launch {
            if (aaruyService?.needlePushAir(aaruyPump.softNeedleType.toByte()) == true) {
                aapsLogger.info(LTag.PUMP, "needlePushAir: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "needlePushAir: failure!")
            }
        }
    }

    fun startBaseInject() {
        scope.launch {
            if (aaruyService?.startBaseInject() == true) {
                aapsLogger.info(LTag.PUMP, "startBaseInject: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startBaseInject: failure!")
            }
        }
    }

    fun onClickUnbind() {
        aaruyService?.unbindDevice()
        aaruyPump.clearAlarmState()
        commandQueue.clearAlarms(null)
    }

    fun connectDevice() {
        if (preferences.get(AaruyStringKey.MacAddress) != "") {
            aaruyService?.connect("retrying connection", preferences.get(AaruyStringKey.MacAddress))
        }
    }

    fun readDailyData(tag: String, fromServer: UByte = 0u) {
        aaruyService?.readDailyData()
    }

    fun requestHistoryData(fromServer: UByte = 0u) {
        aaruyService?.requestHistoryData()
    }

    private fun prepareStep(newStep: ReplaceStep): ReplaceStep {

        replaceStep.postValue(newStep)
        return newStep
    }

    fun resetAlarms() {
        aaruyPump.clearAlarmState()
        commandQueue.clearAlarms(null)
    }
}