package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.Advertisement
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleException
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.CarelevoGatt
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
import app.aaps.pump.carelevo.di.CarelevoCentral
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoConnectNewPatchUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectPrepareEvent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class CarelevoPatchConnectViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val carelevoPatch: CarelevoPatch,
    private val commandQueue: CommandQueue,
    private val preferences: Preferences,
    private val bleSession: CarelevoBleSession,
    @CarelevoCentral private val central: BleCentral,
    private val connectNewPatchUseCase: CarelevoConnectNewPatchUseCase,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase
) : ViewModel() {

    private var _selectedDevice: Advertisement? = null
    private var scanJob: Job? = null

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
     * Discovery scan for the patch service. Results are collected for a short window and the strongest RSSI patch
     * is selected; a patch that tells no name is skipped, as before. A scan that cannot run (no permission) finds
     * nothing.
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
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val found = try {
                val devicesById = linkedMapOf<DeviceId, Advertisement>()
                try {
                    withTimeoutOrNull(DISCOVERY_COLLECTION_MS.milliseconds) {
                        central.scan(ScanFilter(services = listOf(CarelevoGatt.SERVICE))).collect { scanned ->
                            if (scanned.name.isNullOrEmpty()) return@collect
                            val current = devicesById[scanned.device]
                            if (scanned.rssi >= MIN_SCAN_RSSI && (current == null || scanned.rssi > current.rssi)) {
                                devicesById[scanned.device] = scanned
                            }
                        }
                    }
                } catch (e: BleException) {
                    aapsLogger.error(LTag.PUMPCOMM, "newBle.scan failed: ${e.failure}")
                }
                devicesById.values.maxByOrNull { it.rssi }
            } finally {
                _isScanWorking = false
            }
            aapsLogger.info(LTag.PUMPCOMM, "newBle.scan found=${found?.name} address=${found?.device?.value}")
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
            expiry = preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS),
            remains = userSettingInfo.lowInsulinNoticeAmount!!,
            maxBasalSpeed = userSettingInfo.maxBasalSpeed!!,
            maxVolume = userSettingInfo.maxBolusDose!!,
            isBuzzOn = preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
        )

        setUiState(UiState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                // A bond left from an older patch at this address would block the new one. iOS has none to remove.
                central.bonding?.removeBond(device.device)
                delay(commandDelay.milliseconds)
                val pairing = bleSession.runPairing(
                    device.device.value,
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
        // Ends a scan that still runs; its result would belong to the step left.
        scanJob?.cancel()
        scanJob = null
        _selectedDevice = null
        _isScanWorking = false
        setUiState(UiState.Idle)
    }

    private companion object {

        private const val DISCOVERY_COLLECTION_MS = 5_000L
        private const val MIN_SCAN_RSSI = -45
    }
}
