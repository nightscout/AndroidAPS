package app.aaps.pump.carelevo.presentation.viewmodel

import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.core.Connect
import app.aaps.pump.carelevo.ble.core.Disconnect
import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoAlarmViewModel @Inject constructor(
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val uiInteraction: UiInteraction,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val alarmUseCase: CarelevoAlarmInfoUseCase,
    private val alarmClearRequestUseCase: AlarmClearRequestUseCase,
    private val alarmClearPatchDiscardUseCase: AlarmClearPatchDiscardUseCase,
    private val carelevoPumpResumeUseCase: CarelevoPumpResumeUseCase
) : ViewModel() {

    private val _alarmQueue = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarmQueue = _alarmQueue.asStateFlow()

    private val _alarmQueueEmptyEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val alarmQueueEmptyEvent = _alarmQueueEmptyEvent.asSharedFlow()

    private val _event = MutableEventFlow<AlarmEvent>()
    val event = _event.asEventFlow()

    var alarmInfo: CarelevoAlarmInfo? = null

    private val compositeDisposable = CompositeDisposable()

    private val patchAddress: String? = carelevoPatch.getPatchInfoAddress()

    private val sound = app.aaps.core.ui.R.raw.error
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private fun isPatchConnected(): Boolean {
        return carelevoPatch.isCarelevoConnected()
    }

    private fun getConnectedAddress(): String? {
        return patchAddress
    }

    private fun startAlarm(reason: String) {
        if (sound != 0) uiInteraction.startAlarm(sound, reason)
    }

    private fun stopAlarm(reason: String) {
        uiInteraction.stopAlarm(reason)
    }

    fun triggerEvent(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ClearAlarm -> {
                stopAlarm("Confirm Click")
                startAlarmClearProcess(event.info)
            }

            is AlarmEvent.Mute       -> stopAlarm("Mute Click")

            is AlarmEvent.Mute5min   -> {
                stopAlarm("Mute5min Click")
                handler.postDelayed({ startAlarm("post") }, T.mins(5).msecs())
            }

            is AlarmEvent.StartAlarm -> startAlarm("start")
            else                     -> Unit
        }
    }

    fun loadUnacknowledgedAlarms() {
        compositeDisposable += alarmUseCase.getAlarmsOnce()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { optionalList ->
                    val alarms = optionalList.orElse(emptyList())
                        .filter { !it.isAcknowledged }
                        .sortedWith(
                            compareBy<CarelevoAlarmInfo> { it.alarmType.code }
                                .thenBy { it.createdAt }
                        )

                    _alarmQueue.value = alarms.also {
                        if (it.isEmpty()) {
                            _alarmQueueEmptyEvent.tryEmit(Unit)
                        }
                    }

                }, { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "getAlarmsOnce.error error=$e")
                })
    }

    private fun startAlarmClearProcess(info: CarelevoAlarmInfo) {
        alarmInfo = info
        val alarmType = info.alarmType
        val alarmCause = info.cause

        aapsLogger.debug(LTag.PUMPCOMM, "startAlarmClearProcess alarmType=$alarmType, alarmCause=$alarmCause")

        when (alarmCause) {
            AlarmCause.ALARM_WARNING_LOW_INSULIN,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
            AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
            AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
            AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
            AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
            AlarmCause.ALARM_WARNING_PATCH_ERROR,
            AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
            AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
            AlarmCause.ALARM_WARNING_LOW_BATTERY -> {
                if (isPatchConnected()) {
                    startAlarmClearPatchDiscardProcess(info)
                } else {
                    startAlarmClearPatchForceQuitProcess()
                }
            }

            AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                    startInfusionResumeProcess(info)
                } else {
                    triggerEvent(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
                }
            }

            AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {
                startAlarmAlertAbnormalClearProcess(info, alarmCause)
            }

            else -> Unit
        }
    }

    fun startAlarmClearRequestProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += alarmClearRequestUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        aapsLogger.debug(LTag.PUMPCOMM, "acknowledgeAlarm.success alarmId=${info.alarmId}")
                        acknowledgeAndRemoveAlarm(info.alarmId)
                    }, { e ->
                        aapsLogger.error(LTag.PUMPCOMM, "acknowledgeAlarm.error alarmId=${info.alarmId} error=$e")
                    })
        }
    }

    private fun startAlarmAlertAbnormalClearProcess(info: CarelevoAlarmInfo, alarmCause: AlarmCause) {
        viewModelScope.launch {
            compositeDisposable += alarmUseCase.acknowledgeAlarm(info.alarmId)
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        when (alarmCause) {
                            AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {
                                startReconnect(info.alarmId)
                                viewModelScope.launch {
                                    _event.emit(AlarmEvent.RequestBluetoothEnable)
                                }
                            }

                            else                                 -> acknowledgeAndRemoveAlarm(info.alarmId)
                        }

                    }, { error ->

                    })
        }
    }

    private fun startAlarmClearPatchDiscardProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += alarmClearPatchDiscardUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    {
                        startAlarmClearPatchForceQuitProcess()
                    }, { e ->
                        aapsLogger.error(LTag.PUMPCOMM, "clearPatchDiscard.error alarmId=${info.alarmId} error=$e")
                    })
        }
    }

    private fun startInfusionResumeProcess(info: CarelevoAlarmInfo) {
        viewModelScope.launch {
            compositeDisposable += carelevoPumpResumeUseCase.execute()
                .timeout(30L, TimeUnit.SECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    aapsLogger.debug(LTag.PUMPCOMM, "doOnError called : $it")
                }
                .subscribe { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "response success")
                            viewModelScope.launch {
                                pumpSync.syncStopTemporaryBasalWithPumpId(
                                    timestamp = dateUtil.now(),
                                    endPumpId = dateUtil.now(),
                                    pumpType = PumpType.CAREMEDI_CARELEVO,
                                    pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                                )
                            }
                        }

                        is ResponseResult.Failure -> {}

                        is ResponseResult.Error   -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "response failed: ${response.e.message}")
                        }
                    }
                }
        }
    }

    private fun startAlarmClearPatchForceQuitProcess() {
        val address = getConnectedAddress()
        address?.let {
            bleController.clearBond(it)
            compositeDisposable += bleController.execute(Disconnect(address))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.main)
                .subscribe(
                    { result ->
                        aapsLogger.debug(LTag.PUMPCOMM, "startAlarmClearPatchForceQuitProcess result=$result")
                        bleController.unBondDevice()
                        carelevoPatch.flushPatchInformation()
                        clearAllAlarms()
                    }, { e ->
                        aapsLogger.error(LTag.PUMPCOMM, "startAlarmClearPatchForceQuitProcess.disconnectError error=$e")
                    })
        } ?: run {
            bleController.unBondDevice()
            carelevoPatch.flushPatchInformation()
            clearAllAlarms()
        }
    }

    fun acknowledgeAndRemoveAlarm(alarmId: String) {
        _alarmQueue.value = alarmQueue.value.toMutableList().apply {
            removeAll { it.alarmId == alarmId }
        }
        if (alarmQueue.value.isEmpty()) {
            viewModelScope.launch {
                _alarmQueueEmptyEvent.emit(Unit)
            }
        }
    }

    private fun startReconnect(alarmId: String) {
        carelevoPatch.patchInfo.value?.getOrNull()?.let {
            compositeDisposable += bleController.execute(Connect(it.address.uppercase()))
                .observeOn(aapsSchedulers.io)
                .subscribe { result ->
                    when (result) {
                        is CommandResult.Success -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "connect result success")
                            acknowledgeAndRemoveAlarm(alarmId)
                        }

                        else                     -> {
                            aapsLogger.debug(LTag.PUMPCOMM, "connect result failed")
                        }
                    }
                }
        }
    }

    fun clearAllAlarms() {
        compositeDisposable += alarmUseCase.clearAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    _alarmQueue.value = emptyList()
                    val ok = _alarmQueueEmptyEvent.tryEmit(Unit)
                    aapsLogger.debug(LTag.PUMPCOMM, "clearAllAlarms emitEmptyEvent=$ok")
                },
                { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "clearAllAlarms.error error=$e")
                })
    }
}
