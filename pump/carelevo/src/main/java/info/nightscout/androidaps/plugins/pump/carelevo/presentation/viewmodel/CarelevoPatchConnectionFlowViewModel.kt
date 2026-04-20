package info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.CannulaInsertionResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.Result
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchCannulaInsertionConfirmUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoConnectEvent
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoPatchStep
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoPatchConnectionFlowViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val patchObserver: CarelevoPatchObserver,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val patchCannulaInsertionConfirmUseCase: CarelevoPatchCannulaInsertionConfirmUseCase
) : ViewModel() {

    private val _page: MutableStateFlow<CarelevoPatchStep> = MutableStateFlow(CarelevoPatchStep.PATCH_START)
    val page = _page.asStateFlow()

    private var _isCreated = false
    val isCreated get() = _isCreated

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var _inputInsulin = 300
    val inputInsulin get() = _inputInsulin

    private val compositeDisposable = CompositeDisposable()

    fun setIsCreated(isCreated: Boolean) {
        _isCreated = isCreated
    }

    fun setPage(page: CarelevoPatchStep) {
        _page.tryEmit(page)
    }

    fun setInputInsulin(insulin: Int) {
        _inputInsulin = insulin
    }

    fun observePatchEvent() {
        compositeDisposable += patchObserver.patchEvent
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { model ->
                when (model) {
                    is CannulaInsertionResultModel -> {
                        if (model.result != Result.FAILED) {
                            confirmCannulaInsertionResult()
                        }
                    }
                }
            }
    }

    private fun confirmCannulaInsertionResult() {
        compositeDisposable += patchCannulaInsertionConfirmUseCase.execute()
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::confirmCannulaInsertionResult] response success")
                        /*pumpSync.insertTherapyEventIfNewWithTimestamp(
                            timestamp = System.currentTimeMillis(),
                            type = TE.Type.CANNULA_CHANGE,
                            pumpType = PumpType.CAREMEDI_CARELEVO,
                            pumpSerial = carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
                        )*/
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::ConfirmCannulaInsertionResult] response error : ${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::ConfirmCannulaInsertionResult] response failed")
                    }
                }
            }
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectEvent.DiscardComplete -> event
            is CarelevoConnectEvent.DiscardFailed -> event
            else -> CarelevoConnectEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startPatchDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.ConnectedBooted -> {
                startPatchDiscard()
            }

            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectEvent.DiscardComplete)
            }

            else -> {
                startPatchForceDiscard()
            }
        }
    }

    private fun startPatchDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectEvent.DiscardFailed)
            }
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startPatchForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectEvent.DiscardFailed)
            }
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectViewModel::startPatchForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }
                }
            }
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}
