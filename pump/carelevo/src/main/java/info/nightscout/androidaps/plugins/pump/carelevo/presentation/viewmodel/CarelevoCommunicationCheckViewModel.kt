package info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.DiscoveryService
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.EnableNotifications
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalBondingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isDiscoverCleared
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isReInitialized
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeConnected
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeDiscovered
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoCommunicationCheckEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoCommunicationCheckViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val bleController: CarelevoBleController,
    private val carelevoPatch: CarelevoPatch,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    @Named("characterTx") private val txUuid: UUID
) : ViewModel() {

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val connectDisposable = CompositeDisposable()
    private val compositeDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoCommunicationCheckEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoCommunicationCheckEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoCommunicationCheckEvent.ShowMessagePatchAddressInvalid -> event
            is CarelevoCommunicationCheckEvent.CommunicationCheckComplete -> event
            is CarelevoCommunicationCheckEvent.CommunicationCheckFailed -> event
            is CarelevoCommunicationCheckEvent.DiscardComplete -> event
            is CarelevoCommunicationCheckEvent.DiscardFailed -> event
            else -> CarelevoCommunicationCheckEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoCommunicationCheckEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoCommunicationCheckEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoCommunicationCheckEvent.DiscardFailed)
                    }
                }
            }
    }

    fun startReconnect() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoCommunicationCheckEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        val address = carelevoPatch.patchInfo.value?.getOrNull()?.address?.uppercase()
        if (address == null) {
            triggerEvent(CarelevoCommunicationCheckEvent.ShowMessagePatchAddressInvalid)
            return
        }

        connectDisposable += bleController.execute(Connect(address))
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                when (result) {
                    is CommandResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "connect result success")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "connect result failed")
                        cancelReconnect()
                    }
                }
            }

        connectDisposable += carelevoPatch.btState
            .observeOn(aapsSchedulers.io)
            .subscribe { btState ->
                setUiState(UiState.Loading)

                btState.getOrNull()?.let { state ->
                    aapsLogger.debug(LTag.PUMPCOMM, "state : $state")
                    if (state.shouldBeConnected()) {
                        bleController.execute(DiscoveryService(address))
                            .blockingGet()
                            .also { result ->
                                if (result !is CommandResult.Success) cancelReconnect()
                            }.apply {
                                setUiState(UiState.Idle)
                            }

                    }
                    if (state.shouldBeDiscovered()) {
                        bleController.execute(EnableNotifications(address, txUuid))
                            .blockingGet()
                            .also { result ->
                                if (result !is CommandResult.Success) cancelReconnect()
                            }.apply {
                                setUiState(UiState.Idle)
                            }
                        Thread.sleep(2000)
                        triggerEvent(CarelevoCommunicationCheckEvent.CommunicationCheckComplete)
                        connectDisposable.dispose()
                        connectDisposable.clear()
                    }
                    if (state.isDiscoverCleared()) {
                        setUiState(UiState.Idle)
                        cancelReconnect()
                    }
                    if (state.isAbnormalBondingFailed()) {
                        setUiState(UiState.Idle)
                        cancelReconnect()
                    }
                    if (state.isReInitialized()) {
                        setUiState(UiState.Idle)
                        cancelReconnect()
                    }
                }
            }
    }

    private fun cancelReconnect() {
        connectDisposable.dispose()
        connectDisposable.clear()
        triggerEvent(CarelevoCommunicationCheckEvent.CommunicationCheckFailed)
        setUiState(UiState.Idle)
    }

    override fun onCleared() {
        connectDisposable.clear()
        compositeDisposable.clear()
        super.onCleared()
    }
}
