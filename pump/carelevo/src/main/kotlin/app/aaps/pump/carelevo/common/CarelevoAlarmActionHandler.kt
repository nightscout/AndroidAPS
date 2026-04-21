package app.aaps.pump.carelevo.common

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.core.Disconnect
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.AlarmClearRequestUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.alarm.model.AlarmClearUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.infusion.CarelevoPumpResumeUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoAlarmActionHandler @Inject constructor(
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val alarmUseCase: CarelevoAlarmInfoUseCase,
    private val alarmClearRequestUseCase: AlarmClearRequestUseCase,
    private val alarmClearPatchDiscardUseCase: AlarmClearPatchDiscardUseCase,
    private val carelevoPumpResumeUseCase: CarelevoPumpResumeUseCase
) {

    private val _alarmQueue = MutableStateFlow<List<CarelevoAlarmInfo>>(emptyList())
    val alarmQueue = _alarmQueue.asStateFlow()

    private val _alarmQueueEmptyEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val alarmQueueEmptyEvent = _alarmQueueEmptyEvent.asSharedFlow()

    var alarmInfo: CarelevoAlarmInfo? = null

    private val compositeDisposable = CompositeDisposable()

    private val patchAddress: String? = carelevoPatch.getPatchInfoAddress()

    private fun isPatchConnected(): Boolean {
        return carelevoPatch.isCarelevoConnected()
    }

    private fun getConnectedAddress(): String? {
        return patchAddress
    }

    fun observeAlarms() =
        alarmUseCase.observeAlarms()
            .map { it.orElse(emptyList()) }

    fun getAlarmsOnce(includeUnacknowledged: Boolean = true): Single<List<CarelevoAlarmInfo>> =
        alarmUseCase.getAlarmsOnce(includeUnacknowledged)
            .map { it.orElse(emptyList()) }

    fun triggerEvent(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ClearAlarm -> startAlarmClearProcess(event.info)
            else                     -> Unit
        }
    }

    private fun startAlarmClearProcess(info: CarelevoAlarmInfo) {
        alarmInfo = info
        val alarmType = info.alarmType
        val alarmCause = info.cause

        aapsLogger.debug(LTag.PUMPCOMM, "startAlarmClearProcess alarmType=$alarmType, alarmCause=$alarmCause")

        when (alarmCause) {
            AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                    startInfusionResumeProcess(info)
                } else {
                    triggerEvent(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
                }
            }

            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
            AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
            AlarmCause.ALARM_ALERT_APP_NO_USE,
            AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
            AlarmCause.ALARM_ALERT_LOW_BATTERY,
            AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
            AlarmCause.ALARM_NOTICE_LOW_INSULIN,
            AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
            AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK             -> {
                if (isPatchConnected()) {
                    startAlarmClearRequestProcess(info)
                } else {
                    startAlarmAlertAbnormalClearProcess(info, alarmCause)
                }
            }

            AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED               -> {
                startAlarmAlertAbnormalClearProcess(info, alarmCause)
            }

            AlarmCause.ALARM_NOTICE_BG_CHECK,
            AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
            AlarmCause.ALARM_NOTICE_LGS_START,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
            AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
            AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING                -> {
                //startAlarmUpdateProcess()
            }

            AlarmCause.ALARM_UNKNOWN                               -> {
                if (alarmType == AlarmType.WARNING) {
                    if (isPatchConnected()) {
                        startAlarmClearPatchDiscardProcess(info)
                    } else {
                        startAlarmClearPatchForceQuitProcess()
                    }
                } else {
                    //startAlarmUpdateProcess()
                }
            }

            else                                                   -> Unit
        }
    }

    fun startAlarmClearRequestProcess(info: CarelevoAlarmInfo) {
        compositeDisposable += alarmClearRequestUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    aapsLogger.debug(LTag.PUMPCOMM, "acknowledgeAlarm.success alarmId=${info.alarmId}")
                    acknowledgeAndRemoveAlarm(info.alarmId)
                }, { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "acknowledgeAlarm.error alarmId=${info.alarmId} error=$e")
                })
    }

    private fun startAlarmAlertAbnormalClearProcess(info: CarelevoAlarmInfo, alarmCause: AlarmCause) {
        compositeDisposable += alarmUseCase.acknowledgeAlarm(info.alarmId)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    when (alarmCause) {
                        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> {

                        }

                        else                                 -> acknowledgeAndRemoveAlarm(info.alarmId)
                    }

                }, { error ->

                })

    }

    private fun startAlarmClearPatchDiscardProcess(info: CarelevoAlarmInfo) {
        compositeDisposable += alarmClearPatchDiscardUseCase.execute(AlarmClearUseCaseRequest(alarmId = info.alarmId, alarmType = info.alarmType, alarmCause = info.cause))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                {
                    startAlarmClearPatchForceQuitProcess()
                }, { e ->
                    aapsLogger.error(LTag.PUMPCOMM, "clearPatchDiscard.error alarmId=${info.alarmId} error=$e")
                })

    }

    private fun startInfusionResumeProcess(info: CarelevoAlarmInfo) {
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
                        runBlocking {
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

    private fun startAlarmClearPatchForceQuitProcess() {
        val address = getConnectedAddress()
        address?.let {
            bleController.clearBond(it)
            compositeDisposable += bleController.execute(Disconnect(address))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.io)
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
            //_alarmQueueEmptyEvent.emit(Unit)
        }
    }

    fun clearAllAlarms() {
        compositeDisposable += alarmUseCase.clearAlarms()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
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
