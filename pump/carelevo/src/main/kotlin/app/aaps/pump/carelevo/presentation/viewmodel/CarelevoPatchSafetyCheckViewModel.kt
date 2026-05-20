package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.type.SafetyProgress
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchAdditionalPrimingUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchSafetyCheckUseCase
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
    private val bleController: CarelevoBleController,
    private val carelevoPatch: CarelevoPatch,
    private val patchSafetyCheckUseCase: CarelevoPatchSafetyCheckUseCase,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val patchAdditionalPrimingUseCase: CarelevoPatchAdditionalPrimingUseCase
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

        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckProgress)
        compositeDisposable += patchSafetyCheckUseCase.execute()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
            }
            .doFinally {

            }
            .subscribe { response ->
                when (response) {
                    is SafetyProgress.Progress -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response SafetyProgress.Progress - ${response.timeoutSec}")
                        currentTimeoutSec = maxOf(1L, response.timeoutSec)
                        _progress.value = 0
                        _remainSec.value = currentTimeoutSec
                        startTicker(currentTimeoutSec)
                    }

                    is SafetyProgress.Success  -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response SafetyProgress.Success")
                        stopTicker()
                        _progress.value = 100
                        _remainSec.value = 0

                        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
                    }

                    is SafetyProgress.Error    -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response SafetyProgress.Error")
                        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckFailed)
                    }
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
            is PatchState.ConnectedBooted              -> {
                startDiscard()
            }

            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardComplete)
            }

            else                                       -> {
                startForceDiscard()
            }
        }
    }

    private fun startDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                aapsLogger.error(LTag.PUMPCOMM, "doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
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
            }
    }

    private fun startForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                aapsLogger.error(LTag.PUMPCOMM, "doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
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
            }
    }

    fun retryAdditionalPriming() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        if (!carelevoPatch.isCarelevoConnected()) {
            triggerEvent(CarelevoConnectSafetyCheckEvent.ShowMessageCarelevoIsNotConnected)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += patchAdditionalPrimingUseCase.execute()
            .timeout(60, TimeUnit.SECONDS)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .doOnError {
                aapsLogger.error(LTag.PUMPCOMM, "doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectSafetyCheckEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response error : ${response.e}")
                    }

                    else                      -> {
                        aapsLogger.error(LTag.PUMPCOMM, "response failed")
                    }
                }
                setUiState(UiState.Idle)
            }
    }

    fun isSafetyCheckPassed() = carelevoPatch.patchInfo.value?.getOrNull()?.checkSafety == true

    fun isConnected() = carelevoPatch.isCarelevoConnected()

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }

    fun onSafetyCheckComplete() {
        _progress.value = 100
        _remainSec.value = 0
        triggerEvent(CarelevoConnectSafetyCheckEvent.SafetyCheckComplete)
    }
}
