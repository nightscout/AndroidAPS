package info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.DiscoveryService
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.EnableNotifications
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.StartScan
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.StopScan
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.PeripheralScanResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.ScannedDevice
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalBondingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isDiscoverCleared
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isPairingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isReInitialized
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeConnected
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeDiscovered
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeNotificationEnabled
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.MutableEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.asEventFlow
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.Event
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.State
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.UiState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.optionals.getOrNull

@HiltViewModel
class CarelevoPatchConnectViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val sp: SP,
    private val connectNewPatchUseCase: CarelevoConnectNewPatchUseCase,
    private val patchDiscardUseCase: CarelevoPatchDiscardUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    @Named("characterTx") private val txUuid: UUID
) : ViewModel() {

    private var _selectedDevice: ScannedDevice? = null
    val selectedDevice get() = _selectedDevice

    private var _isScanWorking = false
    val isScanWorking get() = _isScanWorking

    private val commandDelay = 300L

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    private val connectDisposable = CompositeDisposable()

    init {
        observeScannedDevice()
    }

    private fun setUiState(state: State) {
        _uiState.tryEmit(state)
    }

    fun triggerEvent(event: Event) {
        viewModelScope.launch {
            when (event) {
                is CarelevoConnectPrepareEvent -> generateEventType(event).run { _event.emit(this) }
            }
        }
    }

    private fun generateEventType(event: Event): Event {
        return when (event) {
            is CarelevoConnectPrepareEvent.ShowConnectDialog -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanFailed -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanIsWorking -> event
            is CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled -> event
            is CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> event
            is CarelevoConnectPrepareEvent.ConnectComplete -> event
            is CarelevoConnectPrepareEvent.ConnectFailed -> event
            is CarelevoConnectPrepareEvent.DiscardComplete -> event
            is CarelevoConnectPrepareEvent.DiscardFailed -> event
            is CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo -> event
            else -> CarelevoConnectPrepareEvent.NoAction
        }
    }

    fun observeScannedDevice() {
        compositeDisposable += CarelevoBleSource.scanDevices
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::observeScannedDeviceTest] device : $it")
                if (it is PeripheralScanResult.Success) {
                    val result = it.value
                    if (result.isNotEmpty()) {
                        _selectedDevice = result[0]
                    }
                }
            }
    }

    fun startScan() {
        if (!bleController.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (isScanWorking) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)
            return
        }

        setUiState(UiState.Loading)
        compositeDisposable += bleController.execute(StartScan())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                _isScanWorking = true
                Thread.sleep(10000)
                setUiState(UiState.Idle)
                stopScan()
            }
    }

    private fun stopScan() {
        compositeDisposable += bleController.execute(StopScan())
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { result ->
                _isScanWorking = false
                if (selectedDevice != null) {
                    triggerEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)
                } else {
                    triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
                }
            }
    }

    fun startPatchDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.ConnectedBooted -> {
                startPatchDiscard()
            }

            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
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
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                }
            }
    }

    private fun startPatchForceDiscard() {
        setUiState(UiState.Loading)
        compositeDisposable += patchForceDiscardUseCase.execute()
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .doOnError {
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] response success")
                        bleController.unBondDevice()
                        carelevoPatch.releasePatch()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startPatchForceDiscard] response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewMode;::startPatchForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                }
            }
    }

    fun startConnect(inputInsulin: Int) {
        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnectTest] startConnectTest called")
        if (!bleController.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (selectedDevice == null) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)
            return
        }

        val address = selectedDevice?.device?.address ?: ""
        connectDisposable += Completable.fromAction {
            bleController.clearBond(address).also {
                aapsLogger.debug(LTag.PUMP, "[PatchConnectConnectViewModel::startConnect] bondRemoveResult : $it")
            }
        }
            .andThen(Completable.timer(commandDelay, TimeUnit.MILLISECONDS))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                       }, { e ->
                aapsLogger.error(LTag.PUMP, "[PatchConnectConnectViewModel::startConnect] bond remove + delay error")
                           stopConnect()
                       })

        connectDisposable += carelevoPatch.btState
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { btState ->
                setUiState(UiState.Loading)
                aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] bt state : $btState")
                btState?.getOrNull()?.let { state ->
                    if (state.shouldBeConnected()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] should be connected called")
                        Thread.sleep(commandDelay)
                        bleController.execute(DiscoveryService(address))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopConnect() }
                    }

                    if (state.shouldBeDiscovered()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] should be discovered called")
                        Thread.sleep(commandDelay)
                        bleController.execute(EnableNotifications(address, txUuid))
                            .blockingGet()
                            .takeIf { it !is CommandResult.Success }
                            ?.let { stopConnect() }
                    }

                    if (state.shouldBeNotificationEnabled()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] should be notification enabled called")
                        Thread.sleep(commandDelay)
                        connectNewPatch(inputInsulin)
                    }
                    if (state.isDiscoverCleared()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] is discover cleared called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if (state.isAbnormalFailed()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] is abnormal failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if (state.isAbnormalBondingFailed()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] is abnormal bonding failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if (state.isReInitialized()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] is reinitialized called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                    if (state.isPairingFailed()) {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] is pairing failed called")
                        Thread.sleep(commandDelay)
                        bleController.clearGatt()
                        stopConnect()
                    }
                }
            }

        connectDisposable += bleController.execute(Connect(address))
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .subscribe { result ->
                when (result) {
                    is CommandResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] connect result success")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::startConnect] connect result failed")
                        stopConnect()
                    }
                }
            }
    }

    private fun stopConnect() {
        connectDisposable.clear()
        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
        setUiState(UiState.Idle)
    }

    private fun connectNewPatch(inputInsulin: Int) {
        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] connectNewPatch called")

        if (!bleController.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] bluetooth is not enabled")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }

        val userSettingInfo = carelevoPatch.userSettingInfo.value?.getOrNull()
        if (userSettingInfo == null) {
            aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] userSettingInfo is null")
            setUiState(UiState.Idle)
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)
            return
        }

        val expiry = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 116)
        val isBuzzOn = sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key, false)

        compositeDisposable += connectNewPatchUseCase.execute(
            CarelevoConnectNewPatchRequestModel(
                volume = inputInsulin,
                expiry = expiry,
                remains = userSettingInfo.lowInsulinNoticeAmount!!,
                maxBasalSpeed = userSettingInfo.maxBasalSpeed!!,
                maxVolume = userSettingInfo.maxBolusDose!!,
                isBuzzOn = isBuzzOn
            )
        )
            .timeout(30000, TimeUnit.MILLISECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribeOn(aapsSchedulers.io)
            .onErrorReturn {
                ResponseResult.Error(it)
            }
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] response success")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectComplete)
                        setUiState(UiState.Idle)
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] response error : ${response.e}")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
                        setUiState(UiState.Idle)
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::connectNewPatch] response failed")
                        triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
                        setUiState(UiState.Idle)
                    }
                }
            }
    }

    override fun onCleared() {
        aapsLogger.debug(LTag.PUMP, "[CarelevoConnectPrepareViewModel::onCleared]")
        connectDisposable.clear()
        super.onCleared()
    }

    fun resetForEnterStep() {
        _selectedDevice = null
        _isScanWorking = false
        bleController.clearScan()
        setUiState(UiState.Idle)
        connectDisposable.clear()
    }
}
