package info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.NeedleCheckFailed
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.NeedleCheckSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.CarelevoSetBasalProgramUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.basal.model.SetBasalProgramRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchNeedleInsertionCheckUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoPatchNeedleInsertionViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val pumpSync: PumpSync,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val patchNeedleInsertionCheckUseCase: CarelevoPatchNeedleInsertionCheckUseCase,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val setBasalProgramUseCase: CarelevoSetBasalProgramUseCase,
    private val carelevoAlarmInfoUseCase: CarelevoAlarmInfoUseCase
) : ViewModel() {

    companion object {

        private const val INSERT_RETRY_DELAY_MS = 150L
        private const val NEEDLE_TO_BASAL_DELAY_MS = 10_000L
    }

    private val _isNeedleInsert: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isNeedleInsert = _isNeedleInsert.asStateFlow()

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _isCreated = false
    val isCreated get() = _isCreated
    private var needleInsertedAtMs: Long? = null
    private var delayedStartBasalJob: Job? = null

    private val compositeDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectNeedleEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoConnectNeedleEvent.ShowMessageProfileNotSet -> event
            is CarelevoConnectNeedleEvent.CheckNeedleComplete -> event
            is CarelevoConnectNeedleEvent.CheckNeedleFailed -> event
            is CarelevoConnectNeedleEvent.CheckNeedleError -> event
            is CarelevoConnectNeedleEvent.DiscardComplete -> event
            is CarelevoConnectNeedleEvent.DiscardFailed -> event
            is CarelevoConnectNeedleEvent.SetBasalComplete -> event
            is CarelevoConnectNeedleEvent.SetBasalFailed -> event
            else -> CarelevoConnectNeedleEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun observePatchInfo() {
        compositeDisposable += carelevoPatch.patchInfo
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                val patchInfo = it?.getOrNull() ?: return@subscribe
                aapsLogger.debug(LTag.PUMP, "[CarelevoPatchNeedleInsertionViewModel] observePatchInfo patchInfo=$patchInfo")
                val isNeedleInserted = patchInfo.checkNeedle ?: false
                _isNeedleInsert.tryEmit(isNeedleInserted)
                if (isNeedleInserted) {
                    if (needleInsertedAtMs == null) needleInsertedAtMs = System.currentTimeMillis()
                } else {
                    needleInsertedAtMs = null
                    delayedStartBasalJob?.cancel()
                }

                val failedCount = patchInfo.needleFailedCount ?: 0
                if (failedCount >= 3) {
                    recordNeedleInsertFailAlarm()
                    triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount))
                }
            }
    }

    fun startCheckNeedle() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += patchNeedleInsertionCheckUseCase.execute()
            .timeout(30, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startCheckNeedle] doOnError called $it")
                setUiState(UiState.Idle)
                val failedCount = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount ?: return@doOnError
                triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount))
            }.subscribe { response ->
                setUiState(UiState.Idle)
                when (response) {
                    is ResponseResult.Success -> {
                        when (val body = response.data) {
                            is NeedleCheckSuccess -> {
                                triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleComplete(true))
                            }

                            is NeedleCheckFailed -> {
                                val failedCount = body.failedCount
                                triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount))
                            }

                            else -> Unit
                        }
                    }

                    else -> {
                        triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleError)
                    }
                }
            }
    }

    fun startSetBasal() {
        val insertedAt = needleInsertedAtMs
        if (insertedAt != null) {
            val elapsed = System.currentTimeMillis() - insertedAt
            val remain = NEEDLE_TO_BASAL_DELAY_MS - elapsed
            if (remain > 0) {
                setUiState(UiState.Loading)
                aapsLogger.debug(
                    LTag.PUMP,
                    "[CarelevoConnectNeedleViewModel::startSetBasal] delayed ${remain}ms (elapsed=${elapsed}ms after needle insert)"
                )
                delayedStartBasalJob?.cancel()
                delayedStartBasalJob = viewModelScope.launch {
                    delay(remain)
                    startSetBasal()
                }
                return
            }
        }

        if (!carelevoPatch.isBluetoothEnabled()) {
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (!carelevoPatch.isCarelevoConnected()) {
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageCarelevoIsNotConnected)
            return
        }
        if (carelevoPatch.profile.value == null) {
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageProfileNotSet)
            return
        }

        carelevoPatch.profile.value?.getOrNull()?.let { profile ->
            setUiState(UiState.Loading)
            compositeDisposable += setBasalProgramUseCase.execute(SetBasalProgramRequestModel(profile))
                .timeout(15000L, TimeUnit.MILLISECONDS)
                .observeOn(aapsSchedulers.io)
                .subscribeOn(aapsSchedulers.io)
                .doOnError {
                    aapsLogger.error(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] response timeout")
                    setUiState(UiState.Idle)
                    triggerEvent(CarelevoConnectNeedleEvent.SetBasalFailed)
                }.subscribe { response ->
                    when (response) {
                        is ResponseResult.Success -> {
                            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] response success")
                            val serial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                            pumpSync.connectNewPump(true)
                            Thread.sleep(1000)
                            insertTherapyEventWithSingleRetry(TE.Type.CANNULA_CHANGE, serial)
                            insertTherapyEventWithSingleRetry(TE.Type.INSULIN_CHANGE, serial)
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectNeedleEvent.SetBasalComplete)
                        }

                        is ResponseResult.Error -> {
                            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] response error : ${response.e}")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectNeedleEvent.SetBasalFailed)
                        }

                        else -> {
                            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] response failed")
                            setUiState(UiState.Idle)
                            triggerEvent(CarelevoConnectNeedleEvent.SetBasalFailed)
                        }
                    }
                }
        } ?: run {
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageProfileNotSet)
        }
    }

    private fun insertTherapyEventWithSingleRetry(type: TE.Type, serial: String) {
        viewModelScope.launch {
            var inserted = pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp = System.currentTimeMillis(),
                type = type,
                pumpType = PumpType.CAREMEDI_CARELEVO,
                pumpSerial = serial
            )
            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] $type insert result=$inserted serial=$serial")
            if (!inserted) {
                SystemClock.sleep(INSERT_RETRY_DELAY_MS)
                inserted = pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    pumpType = PumpType.CAREMEDI_CARELEVO,
                    pumpSerial = serial
                )
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startSetBasal] $type recovery insert result=$inserted serial=$serial")
            }
        }
    }

    fun startDiscardProcess() {
        if (!carelevoPatch.isCarelevoConnected()) {
            startForceDiscard()
        } else {
            startDiscard()
        }
    }

    private fun startDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectNeedleViewModel::startForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun recordNeedleInsertFailAlarm() {
        val info = CarelevoAlarmInfo(
            alarmId = System.currentTimeMillis().toString(),
            alarmType = AlarmType.WARNING,
            cause = AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR,
            createdAt = LocalDateTime.now().toString(),
            updatedAt = LocalDateTime.now().toString(),
            isAcknowledged = false,

            )
        compositeDisposable += carelevoAlarmInfoUseCase.upsertAlarm(info)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                { aapsLogger.debug(LTag.PUMP, "[CarelevoPatchNeedleInsertionViewModel] recordNeedleInsertFailAlarm.upsertComplete") },
                { e -> aapsLogger.error(LTag.PUMP, "[CarelevoPatchNeedleInsertionViewModel] recordNeedleInsertFailAlarm.upsertError error=$e") }
            )
    }

    fun needleFailCount() = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount

    override fun onCleared() {
        delayedStartBasalJob?.cancel()
        compositeDisposable.clear()
        super.onCleared()
    }
}
