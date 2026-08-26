package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class CarelevoPatchConnectViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val commandQueue: CommandQueue,
    private val sp: SP,
    private val bleSession: CarelevoBleSession,
    private val transport: CarelevoBleTransport,
    private val connectNewPatchUseCase: CarelevoConnectNewPatchUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
) : ViewModel() {

    private var _selectedDevice: ScannedDevice? = null

    private var _isScanWorking = false
    val isScanWorking get() = _isScanWorking

    private val commandDelay = 300L

    private val _event = MutableEventFlow<Event>()
    val event = _event.asEventFlow()

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

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
            is CarelevoConnectPrepareEvent.ShowConnectDialog                 -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanFailed             -> event
            is CarelevoConnectPrepareEvent.ShowMessageScanIsWorking          -> event
            is CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled    -> event
            is CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty -> event
            is CarelevoConnectPrepareEvent.ConnectComplete                   -> event
            is CarelevoConnectPrepareEvent.ConnectFailed                     -> event
            is CarelevoConnectPrepareEvent.DiscardComplete                   -> event
            is CarelevoConnectPrepareEvent.DiscardFailed                     -> event
            is CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo  -> event
            else                                                             -> CarelevoConnectPrepareEvent.NoAction
        }
    }

    /**
     * Discovery scan over the transport. `scanAddress = null` puts `CarelevoBleTransportImpl`'s scanner
     * in service-UUID discovery mode; results are collected for a short window and the strongest RSSI
     * patch is selected.
     */
    fun startScan() {
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        if (isScanWorking) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanIsWorking)
            return
        }

        setUiState(UiState.Loading)
        _isScanWorking = true
        viewModelScope.launch(Dispatchers.IO) {
            val found = try {
                transport.scanAddress = null
                val devicesByAddress = linkedMapOf<String, ScannedDevice>()
                withTimeoutOrNull(DISCOVERY_COLLECTION_MS.milliseconds) {
                    transport.scanner.scannedDevices
                        // Subscribe BEFORE starting the scan so an immediate advertisement cannot be missed.
                        .onSubscription { transport.scanner.startScan() }
                        .collect { scanned ->
                            val current = devicesByAddress[scanned.address]
                            if (scanned.rssi >= MIN_SCAN_RSSI && (current == null || scanned.rssi > current.rssi)) {
                                devicesByAddress[scanned.address] = scanned
                            }
                        }
                }
                devicesByAddress.values.maxByOrNull { it.rssi }
            } finally {
                transport.scanner.stopScan()
                _isScanWorking = false
            }
            aapsLogger.info(LTag.PUMPCOMM, "newBle.scan found=${found?.name} address=${found?.address}")
            _selectedDevice = found
            setUiState(UiState.Idle)
            if (found != null) {
                triggerEvent(CarelevoConnectPrepareEvent.ShowConnectDialog)
            } else {
                triggerEvent(CarelevoConnectPrepareEvent.ShowMessageScanFailed)
            }
        }
    }

    fun startPatchDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
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
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    } else {
                        aapsLogger.error(LTag.PUMPCOMM, "discard failed, falling back to force-discard")
                        startPatchForceDiscard()
                    }
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
                aapsLogger.debug(LTag.PUMPCOMM, "doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
            }.subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        carelevoPatch.discardTeardown()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardComplete)
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }

                    else                      -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "[CarelevoConnectPrepareViewMode;::startPatchForceDiscard] response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectPrepareEvent.DiscardFailed)
                    }
                }
            }
    }

    /**
     * Pair the scanned patch: clear any stale bond → one
     * [CarelevoBleSession.runPairing] session (connect → bond → MAC/auth/set-time→patch-info/alarm/
     * threshold) → persist via the use case's `persistNewPatch`. No btState observer needed — the session
     * owns its connect handshake and either returns or throws. After success the CommandQueue (activation
     * customCommands) takes over.
     */
    fun startConnect(inputInsulin: Int) {
        aapsLogger.debug(LTag.PUMPCOMM, "startConnect called")
        if (!carelevoPatch.isBluetoothEnabled()) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageBluetoothNotEnabled)
            return
        }
        val device = _selectedDevice
        if (device == null) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageSelectedDeviceIseEmpty)
            return
        }
        val userSettingInfo = carelevoPatch.userSettingInfo.value?.getOrNull()
        if (userSettingInfo == null) {
            triggerEvent(CarelevoConnectPrepareEvent.ShowMessageNotSetUserSettingInfo)
            return
        }
        val request = CarelevoConnectNewPatchRequestModel(
            volume = inputInsulin,
            expiry = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 116),
            remains = userSettingInfo.lowInsulinNoticeAmount!!,
            maxBasalSpeed = userSettingInfo.maxBasalSpeed!!,
            maxVolume = userSettingInfo.maxBolusDose!!,
            isBuzzOn = sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key, false)
        )

        setUiState(UiState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                transport.adapter.removeBond(device.address)
                delay(commandDelay.milliseconds)
                val pairing = bleSession.runPairing(
                    device.address,
                    CarelevoBleSession.PairingSpec(
                        volume = request.volume,
                        remains = request.remains,
                        expiry = request.expiry,
                        maxBasalSpeed = request.maxBasalSpeed,
                        maxBolusDose = request.maxVolume,
                        buzzUse = request.isBuzzOn
                    )
                )
                check(
                    connectNewPatchUseCase.persistNewPatch(
                        address = pairing.address,
                        serialNumber = pairing.serialNumber,
                        firmwareVersion = pairing.firmwareVersion,
                        modelName = pairing.modelName,
                        request = request
                    )
                ) { "persist new patch failed" }
                pairing
            }
            setUiState(UiState.Idle)
            outcome.fold(
                onSuccess = { pairing ->
                    aapsLogger.info(LTag.PUMPCOMM, "newBle.pairing OK serial=${pairing.serialNumber} address=${pairing.address}")
                    triggerEvent(CarelevoConnectPrepareEvent.ConnectComplete)
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    aapsLogger.error(LTag.PUMPCOMM, "newBle.pairing FAILED", e)
                    triggerEvent(CarelevoConnectPrepareEvent.ConnectFailed)
                }
            )
        }
    }

    override fun onCleared() {
        aapsLogger.debug(LTag.PUMPCOMM, "onCleared")
        compositeDisposable.clear()
    }

    fun resetForEnterStep() {
        _selectedDevice = null
        _isScanWorking = false
        transport.scanner.stopScan()
        setUiState(UiState.Idle)
    }

    private companion object {

        private const val DISCOVERY_COLLECTION_MS = 5_000L
        private const val MIN_SCAN_RSSI = -45
    }
}
