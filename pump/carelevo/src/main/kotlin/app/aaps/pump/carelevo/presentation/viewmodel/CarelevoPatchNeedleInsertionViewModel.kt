package app.aaps.pump.carelevo.presentation.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdNeedleCheck
import app.aaps.pump.carelevo.command.CmdSetBasal
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectNeedleEvent
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
    private val persistenceLayer: PersistenceLayer,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val commandQueue: CommandQueue,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
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
                aapsLogger.debug(LTag.PUMPCOMM, "observePatchInfo patchInfo=$patchInfo")
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

        setUiState(UiState.Loading)
        // Routed through the CommandQueue: it connects (and reconnects if the link dropped) before
        // running the check on the queue worker thread, so a mid-activation drop is handled.
        viewModelScope.launch {
            val result = commandQueue.customCommand(CmdNeedleCheck())
            setUiState(UiState.Idle)
            if (result.success) {
                triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleComplete(true))
            } else {
                // A failed check leaves the failure count on the patch; a communication failure
                // (queue could not reach the patch) leaves no count → surface a generic error.
                val failedCount = needleFailCount()
                if (failedCount != null) {
                    triggerEvent(CarelevoConnectNeedleEvent.CheckNeedleFailed(failedCount))
                } else {
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
                    LTag.PUMPCOMM,
                    "delayed ${remain}ms (elapsed=${elapsed}ms after needle insert)"
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
        if (carelevoPatch.profile.value?.getOrNull() == null) {
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectNeedleEvent.ShowMessageProfileNotSet)
            return
        }

        setUiState(UiState.Loading)
        // Routed through the CommandQueue (connect/reconnect-before-execute). The executor reads the
        // profile off the patch and programs the basal; the post-success bookkeeping (pump sync +
        // therapy events) stays here on the VM.
        viewModelScope.launch {
            val result = commandQueue.customCommand(CmdSetBasal())
            if (result.success) {
                aapsLogger.debug(LTag.PUMPCOMM, "set basal success")
                val serial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                pumpSync.connectNewPump(true)
                delay(1000)
                insertCannulaChangeWithSite(serial, System.currentTimeMillis())
                insertTherapyEventWithSingleRetry(TE.Type.INSULIN_CHANGE, serial)
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectNeedleEvent.SetBasalComplete)
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "set basal failed")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectNeedleEvent.SetBasalFailed)
            }
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
            aapsLogger.debug(LTag.PUMPCOMM, "$type insert result=$inserted serial=$serial")
            if (!inserted) {
                SystemClock.sleep(INSERT_RETRY_DELAY_MS)
                inserted = pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    pumpType = PumpType.CAREMEDI_CARELEVO,
                    pumpSerial = serial
                )
                aapsLogger.debug(LTag.PUMPCOMM, "$type recovery insert result=$inserted serial=$serial")
            }
        }
    }

    /**
     * Insert the CANNULA_CHANGE event and, if a site location was chosen in the wizard's
     * site-location step, patch it onto that event (matches Medtrum/Equil behaviour).
     */
    private fun insertCannulaChangeWithSite(serial: String, timestamp: Long) {
        viewModelScope.launch {
            var inserted = pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp = timestamp,
                type = TE.Type.CANNULA_CHANGE,
                pumpType = PumpType.CAREMEDI_CARELEVO,
                pumpSerial = serial
            )
            aapsLogger.debug(LTag.PUMPCOMM, "CANNULA_CHANGE insert result=$inserted serial=$serial")
            if (!inserted) {
                SystemClock.sleep(INSERT_RETRY_DELAY_MS)
                inserted = pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = timestamp,
                    type = TE.Type.CANNULA_CHANGE,
                    pumpType = PumpType.CAREMEDI_CARELEVO,
                    pumpSerial = serial
                )
                aapsLogger.debug(LTag.PUMPCOMM, "CANNULA_CHANGE recovery insert result=$inserted serial=$serial")
            }
            saveSiteLocationToTherapyEvent(timestamp)
        }
    }

    /** Patch the chosen site location/arrow onto the CANNULA_CHANGE therapy event just recorded. */
    private suspend fun saveSiteLocationToTherapyEvent(timestamp: Long) {
        val location = carelevoPatch.sitePlacementLocation.takeIf { it != TE.Location.NONE }
        val arrow = carelevoPatch.sitePlacementArrow.takeIf { it != TE.Arrow.NONE }
        if (location == null && arrow == null) return
        try {
            persistenceLayer.getTherapyEventDataFromToTime(timestamp, timestamp)
                .firstOrNull { it.type == TE.Type.CANNULA_CHANGE }
                ?.let { te ->
                    persistenceLayer.insertOrUpdateTherapyEvent(te.copy(location = location, arrow = arrow))
                }
        } catch (_: Exception) {
            // site location is optional; ignore failures
        }
    }

    fun startDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectNeedleEvent.DiscardComplete)
            }

            else                                       -> {
                // Route the BLE stop through the queue (reconnect-before-execute); if the patch can't
                // be reached at all, fall back to the DB-only force-discard.
                setUiState(UiState.Loading)
                viewModelScope.launch {
                    val result = commandQueue.customCommand(CmdDiscard())
                    if (result.success) {
                        // unBond + releasePatch run inside CmdDiscard on the queue thread
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectNeedleEvent.DiscardComplete)
                    } else {
                        aapsLogger.error(LTag.PUMPCOMM, "discard failed, falling back to force-discard")
                        startForceDiscard()
                    }
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
            .subscribe({ response ->
                           when (response) {
                               is ResponseResult.Success -> {
                                   aapsLogger.debug(LTag.PUMPCOMM, "response success")
                                   carelevoPatch.discardTeardown()
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectNeedleEvent.DiscardComplete)
                               }

                               is ResponseResult.Error   -> {
                                   aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                               }

                               else                      -> {
                                   aapsLogger.debug(LTag.PUMPCOMM, "response failed")
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                               }
                           }
                       }, { e ->
                           aapsLogger.debug(LTag.PUMPCOMM, "force discard failed : $e")
                           setUiState(UiState.Idle)
                           triggerEvent(CarelevoConnectNeedleEvent.DiscardFailed)
                       })
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
                { aapsLogger.debug(LTag.PUMPCOMM, "recordNeedleInsertFailAlarm.upsertComplete") },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "recordNeedleInsertFailAlarm.upsertError error=$e") }
            )
    }

    fun needleFailCount() = carelevoPatch.patchInfo.value?.getOrNull()?.needleFailedCount

    override fun onCleared() {
        delayedStartBasalJob?.cancel()
        compositeDisposable.clear()
        super.onCleared()
    }
}
