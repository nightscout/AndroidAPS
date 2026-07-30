package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.command.CmdAdditionalPriming
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.command.CmdSafetyCheck
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectSafetyCheckEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoPatchSafetyCheckViewModel @Inject constructor(
    private val aapsSchedulers: AapsSchedulers,
    private val aapsLogger: AAPSLogger,
    private val carelevoPatch: CarelevoPatch,
    private val commandQueue: CommandQueue,
    private val activationExecutor: CarelevoActivationExecutor,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
) : ViewModel() {

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState = MutableStateFlow<State>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _progress = MutableStateFlow<Int?>(null)
    val progress = _progress.asStateFlow()

    private val _remainSec = MutableStateFlow<Long?>(null)
    val remainSec = _remainSec.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    private var tickerDisposable: Disposable? = null
    private var currentTimeoutSec: Long = 0L
    private val timeTickerDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectSafetyCheckEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled    -> event
            is CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected -> event
            is CarelevoConnectSafetyCheckEvent.SafetyCheckProgress               -> event
            is CarelevoConnectSafetyCheckEvent.SafetyCheckComplete               -> event
            is CarelevoConnectSafetyCheckEvent.SafetyCheckFailed                 -> event
            is CarelevoConnectSafetyCheckEvent.DiscardComplete                   -> event
            is CarelevoConnectSafetyCheckEvent.DiscardFailed                     -> event
            else                                                                 -> CarelevoConnectSafetyCheckEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startSafetyCheck() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        // Route through the CommandQueue: it reconnects if the link dropped, runs the check on the
        // queue thread, and returns a real success/fail result.
        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)
        viewModelScope.launch {
            val progressJob = launch {
                activationExecutor.safetyProgress.collect { progress ->
                    if (progress is SafetyProgress.Progress) {
                        currentTimeoutSec = maxOf(1L, progress.timeoutSec)
                        _progress.value = 0
                        _remainSec.value = currentTimeoutSec
                        startTicker(currentTimeoutSec)
                    }
                }
            }
            val result = commandQueue.customCommand(CmdSafetyCheck())
            progressJob.cancel()
            stopTicker()
            if (result.success) {
                aapsLogger.debug(LTag.PUMPCOMM, "safety check success")
                _progress.value = 100
                _remainSec.value = 0
                triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
            } else {
                aapsLogger.error(LTag.PUMPCOMM, "safety check failed")
                triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
            }
        }
    }

    private fun startTicker(sec: Long) {
        val timeoutSec = sec - 30
        stopTicker()

        tickerDisposable = Observable.intervalRange(0, timeoutSec + 1, 0, 1, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe { tick ->
                val percent = ((tick.toDouble() / timeoutSec) * 100.0)
                    .coerceIn(0.0, 100.0)
                    .toInt()

                _progress.value = maxOf(_progress.value ?: 0, percent)
                _remainSec.value = (timeoutSec - tick).coerceAtLeast(0)

                aapsLogger.debug(LTag.UI, "percent: $percent, remain: ${_remainSec.value}")
            }

        tickerDisposable?.let(timeTickerDisposable::add)
    }

    private fun stopTicker() {
        tickerDisposable?.dispose()
        tickerDisposable = null
    }

    fun startDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
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
                        triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
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
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ response ->
                           when (response) {
                               is ResponseResult.Success -> {
                                   aapsLogger.debug(LTag.PUMPCOMM, "response success")
                                   carelevoPatch.discardTeardown()
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
                               }

                               is ResponseResult.Error   -> {
                                   aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                               }

                               else                      -> {
                                   aapsLogger.error(LTag.PUMPCOMM, "response failed")
                                   setUiState(UiState.Idle)
                                   triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                               }
                           }
                       }, { e ->
                           aapsLogger.error(LTag.PUMPCOMM, "force discard failed : $e")
                           setUiState(UiState.Idle)
                           triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
                       })
    }

    fun retryAdditionalPriming() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        setUiState(UiState.Loading)
        // Routed through the CommandQueue (connect/reconnect-before-execute). Best-effort re-prime to
        // push a droplet out; on failure surface feedback rather than silently going idle.
        viewModelScope.launch {
            val result = commandQueue.customCommand(CmdAdditionalPriming())
            setUiState(UiState.Idle)
            if (!result.success) {
                aapsLogger.error(LTag.PUMPCOMM, "additional priming failed")
                triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
            }
        }
    }

    fun isSafetyCheckPassed() = carelevoPatch.patchInfo.value?.getOrNull()?.checkSafety == true

    // Per-op sessions leave no resting link: "connected" = a session can be attempted (patch paired + BT on).
    fun isConnected() = carelevoPatch.getPatchInfoAddress() != null && carelevoPatch.isBluetoothEnabled()

    override fun onCleared() {
        compositeDisposable.clear()
    }

    fun onSafetyCheckComplete() {
        _progress.value = 100
        _remainSec.value = 0
        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }
}
