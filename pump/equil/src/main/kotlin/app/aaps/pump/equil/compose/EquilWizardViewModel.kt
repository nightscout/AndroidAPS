package app.aaps.pump.equil.compose

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.extensions.safeEnable
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.GattAttributes
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.events.EventEquilUnPairChanged
import app.aaps.pump.equil.keys.EquilIntPreferenceKey
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils
import app.aaps.pump.equil.manager.command.CmdAlarmSet
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdDevicesGet
import app.aaps.pump.equil.manager.command.CmdDevicesOldGet
import app.aaps.pump.equil.manager.command.CmdInsulinChange
import app.aaps.pump.equil.manager.command.CmdInsulinGet
import app.aaps.pump.equil.manager.command.CmdModelSet
import app.aaps.pump.equil.manager.command.CmdPair
import app.aaps.pump.equil.manager.command.CmdResistanceGet
import app.aaps.pump.equil.manager.command.CmdSettingSet
import app.aaps.pump.equil.manager.command.CmdStepSet
import app.aaps.pump.equil.manager.command.CmdTimeSet
import app.aaps.pump.equil.manager.command.CmdUnPair
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EquilWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val blePreCheck: BlePreCheck,
    private val commandQueue: CommandQueue,
    private val equilPumpPlugin: EquilPumpPlugin,
    private val equilManager: EquilManager,
    private val pumpSync: PumpSync,
    private val equilHistoryRecordDao: EquilHistoryRecordDao,
    private val constraintsChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val rxBus: RxBus
) : ViewModel() {

    // region State

    private val _wizardStep = MutableStateFlow<EquilWizardStep?>(null)
    val wizardStep: StateFlow<EquilWizardStep?> = _wizardStep.asStateFlow()

    private val _workflow = MutableStateFlow(EquilWorkflow.PAIR)
    val workflow: StateFlow<EquilWorkflow> = _workflow.asStateFlow()

    private val _totalSteps = MutableStateFlow(6)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _titleResId = MutableStateFlow(R.string.equil_title_assemble)
    val titleResId: StateFlow<Int> = _titleResId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Fill step state
    private val _fillComplete = MutableStateFlow(false)
    val fillComplete: StateFlow<Boolean> = _fillComplete.asStateFlow()

    private val _autoFilling = MutableStateFlow(false)
    val autoFilling: StateFlow<Boolean> = _autoFilling.asStateFlow()

    // Scan state
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    // Air step state
    private val _airRemovalDone = MutableStateFlow(false)
    val airRemovalDone: StateFlow<Boolean> = _airRemovalDone.asStateFlow()

    // Unpair result (shown as OkDialog after unpair completes)
    private val _unpairResultMessage = MutableStateFlow<String?>(null)
    val unpairResultMessage: StateFlow<String?> = _unpairResultMessage.asStateFlow()

    // Events
    private val _events = MutableSharedFlow<EquilWizardEvent>()
    val events: SharedFlow<EquilWizardEvent> = _events.asSharedFlow()

    // endregion

    // region BLE scan internals
    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var scanSettings: ScanSettings? = null
    private var scanFilters: List<ScanFilter>? = null
    private var isScanActive = false
    private val scanHandler = Handler(HandlerThread("EquilScanHandler").also { it.start() }.looper)
    private var serialNumber = ""
    private var password = ""
    @Volatile private var fillStepCount = 0
    @Volatile private var autoFillActive = false
    // endregion

    sealed class EquilWizardEvent {
        data object Finish : EquilWizardEvent()
        data class ShowMessage(val message: String) : EquilWizardEvent()
    }

    // region Initialization

    fun initializeWorkflow(workflow: EquilWorkflow) {
        _workflow.value = workflow
        _totalSteps.value = workflow.totalSteps
        _errorMessage.value = null
        _isLoading.value = false
        _fillComplete.value = false
        _autoFilling.value = false
        _scanning.value = false
        _scanError.value = null
        _airRemovalDone.value = false
        _unpairResultMessage.value = null
        _serialNumberDisplay.value = equilManager.equilState?.serialNumber ?: ""
        fillStepCount = 0
        autoFillActive = false

        val startStep = when (workflow) {
            EquilWorkflow.PAIR           -> EquilWizardStep.ASSEMBLE
            EquilWorkflow.CHANGE_INSULIN -> EquilWizardStep.CHANGE_INSULIN
            EquilWorkflow.UNPAIR         -> EquilWizardStep.UNPAIR_DETACH
        }
        moveStep(startStep)
    }

    // endregion

    // region Navigation

    fun moveStep(step: EquilWizardStep) {
        _errorMessage.value = null
        _isLoading.value = false
        _wizardStep.value = step
        updateStepProgress(step)
    }

    private fun updateStepProgress(step: EquilWizardStep) {
        when (_workflow.value) {
            EquilWorkflow.PAIR           -> {
                val (index, title) = when (step) {
                    EquilWizardStep.ASSEMBLE      -> 0 to R.string.equil_title_assemble
                    EquilWizardStep.SERIAL_NUMBER -> 1 to R.string.equil_title_serial
                    EquilWizardStep.FILL          -> 2 to R.string.equil_title_fill
                    EquilWizardStep.ATTACH        -> 3 to R.string.equil_title_attach
                    EquilWizardStep.AIR           -> 4 to R.string.equil_title_air
                    EquilWizardStep.CONFIRM       -> 5 to R.string.equil_title_confirm
                    else                          -> 0 to R.string.equil_common_wizard_button_next
                }
                _currentStepIndex.value = index
                _titleResId.value = title
            }

            EquilWorkflow.CHANGE_INSULIN -> {
                val (index, title) = when (step) {
                    EquilWizardStep.CHANGE_INSULIN -> 0 to R.string.equil_change
                    EquilWizardStep.ASSEMBLE       -> 1 to R.string.equil_title_dressing
                    EquilWizardStep.FILL           -> 2 to R.string.equil_title_fill
                    EquilWizardStep.ATTACH         -> 3 to R.string.equil_title_attach
                    EquilWizardStep.AIR            -> 4 to R.string.equil_title_air
                    EquilWizardStep.CONFIRM        -> 5 to R.string.equil_title_confirm
                    else                           -> 0 to R.string.equil_change
                }
                _currentStepIndex.value = index
                _titleResId.value = title
            }

            EquilWorkflow.UNPAIR         -> {
                val (index, title) = when (step) {
                    EquilWizardStep.UNPAIR_DETACH  -> 0 to R.string.equil_title_unpair_detach
                    EquilWizardStep.UNPAIR_CONFIRM -> 1 to R.string.equil_title_unpair_confirm
                    else                           -> 0 to R.string.equil_title_unpair_detach
                }
                _currentStepIndex.value = index
                _titleResId.value = title
            }
        }
    }

    fun handleCancel() {
        stopBLEScan()
        autoFillActive = false
        _autoFilling.value = false
        viewModelScope.launch { _events.emit(EquilWizardEvent.Finish) }
    }

    fun handleComplete() {
        viewModelScope.launch { _events.emit(EquilWizardEvent.Finish) }
    }

    val canGoBack: StateFlow<Boolean> = _wizardStep.mapState { step ->
        step in listOf(
            EquilWizardStep.ASSEMBLE,
            EquilWizardStep.CHANGE_INSULIN,
            EquilWizardStep.UNPAIR_DETACH
        )
    }

    /** Maps a StateFlow to a derived StateFlow. Call only at property init time — not inside composables. */
    private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
        val result = MutableStateFlow(transform(value))
        viewModelScope.launch {
            collect { result.value = transform(it) }
        }
        return result.asStateFlow()
    }

    // endregion

    // region Assemble step

    fun getAssembleNextStep(): EquilWizardStep =
        when (_workflow.value) {
            EquilWorkflow.PAIR           -> EquilWizardStep.SERIAL_NUMBER
            EquilWorkflow.CHANGE_INSULIN -> EquilWizardStep.FILL
            EquilWorkflow.UNPAIR         -> error("Assemble step is unreachable in UNPAIR workflow")
        }

    // endregion

    // region Serial Number / BLE Scan step

    fun prepareBLEScan(): Boolean {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanFilters = listOf(
            ScanFilter.Builder().setServiceUuid(
                ParcelUuid.fromString(GattAttributes.SERVICE_RADIO)
            ).build()
        )
        return bleScanner != null
    }

    @SuppressLint("MissingPermission")
    fun startBLEScan(serial: String, pwd: String) {
        if (bleScanner == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "startBLEScan failed: bleScanner is null")
            return
        }
        serialNumber = serial
        password = pwd
        preferences.put(EquilStringKey.PairPassword, password)
        equilManager.setAddress("")
        equilManager.setSerialNumber("")

        isScanActive = true
        _scanning.value = true
        _scanError.value = null

        scanHandler.postDelayed(scanTimeoutRunnable, SCAN_PERIOD_MILLIS)
        if (bluetoothAdapter?.isEnabled != true) bluetoothAdapter?.safeEnable(waitMilliseconds = 3000)
        bleScanner?.startScan(scanFilters, scanSettings, bleScanCallback)
        aapsLogger.debug(LTag.PUMPCOMM, "startBLEScan: Scanning Start for $serial")
    }

    @SuppressLint("MissingPermission")
    fun stopBLEScan() {
        if (isScanActive) {
            isScanActive = false
            _scanning.value = false
            scanHandler.removeCallbacks(scanTimeoutRunnable)
            if (bluetoothAdapter?.isEnabled == true && bluetoothAdapter?.state == BluetoothAdapter.STATE_ON) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(bleScanCallback)
                }
            }
            aapsLogger.debug(LTag.PUMPBTCOMM, "stopBLEScan: Scanning Stop")
        }
    }

    private val scanTimeoutRunnable = Runnable {
        if (isScanActive) {
            stopBLEScan()
            _scanError.value = rh.gs(R.string.equil_pair_error)
            _scanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, scanRecord: ScanResult) {
            aapsLogger.debug(LTag.PUMPBTCOMM, scanRecord.toString())
            val name = scanRecord.device.name
            if (!TextUtils.isEmpty(name) && name.contains(serialNumber)) {
                scanRecord.scanRecord?.bytes?.let {
                    val historyIndex = Utils.bytesToInt(it[24], it[23])
                    equilManager.setStartHistoryIndex(historyIndex)
                    aapsLogger.debug(LTag.PUMPCOMM, "historyIndex $historyIndex")
                }
                scanHandler.removeCallbacks(scanTimeoutRunnable)
                stopBLEScan()
                getVersion(scanRecord.device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {}
    }

    private fun getVersion(device: BluetoothDevice) {
        val cmdDevicesOldGet = CmdDevicesOldGet(device.address, aapsLogger, preferences, equilManager)
        commandQueue.customCommand(cmdDevicesOldGet, object : Callback() {
            override fun run() {
                aapsLogger.debug(LTag.PUMPCOMM, "getVersion result=${result.success}")
                if (result.success) {
                    if (cmdDevicesOldGet.isSupport(serialNumber)) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            pair(device)
                        }
                    } else {
                        _scanning.value = false
                        _scanError.value = rh.gs(R.string.equil_support_error)
                    }
                } else {
                    _scanning.value = false
                    _scanError.value = rh.gs(R.string.equil_pair_error)
                    equilManager.setAddress("")
                    equilManager.setSerialNumber("")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun pair(device: BluetoothDevice) {
        equilManager.setActivationProgress(ActivationProgress.PRIMING)
        equilManager.setBluetoothConnectionState(BluetoothConnectionState.CONNECTED)
        aapsLogger.debug(LTag.PUMPCOMM, "pair: ${device.name} / ${device.address}")
        commandQueue.customCommand(
            CmdPair(device.name.toString(), device.address, password, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    aapsLogger.debug(LTag.PUMPCOMM, "pair result=${result.success}, enacted=${result.enacted}")
                    if (result.success) {
                        if (result.enacted) {
                            viewModelScope.launch {
                                delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                                pumpSettings(device.address, device.name.toString())
                            }
                        } else {
                            _scanning.value = false
                            _scanError.value = rh.gs(R.string.equil_password_error)
                        }
                    } else {
                        _scanning.value = false
                        _scanError.value = rh.gs(R.string.equil_pair_error)
                        equilManager.setAddress("")
                        equilManager.setSerialNumber("")
                    }
                }
            }
        )
    }

    private fun pumpSettings(address: String, serial: String) {
        val profile = pumpSync.expectedPumpState().profile ?: return
        commandQueue.customCommand(
            CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), constraintsChecker.getMaxBasalAllowed(profile).value(), aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        equilManager.setAddress(address)
                        equilManager.setSerialNumber(serial)
                        equilManager.setActivationProgress(ActivationProgress.CANNULA_CHANGE)
                        _scanning.value = false
                        _scanError.value = null
                        moveStep(EquilWizardStep.FILL)
                    } else {
                        _scanning.value = false
                        _scanError.value = rh.gs(R.string.equil_pair_error)
                        equilManager.setAddress("")
                        equilManager.setSerialNumber("")
                    }
                }
            }
        )
    }

    // endregion

    // region Fill step

    fun startFill() {
        autoFillActive = true
        _autoFilling.value = true
        _fillComplete.value = false
        _errorMessage.value = null
        fillSetStep()
    }

    fun stopFill() {
        autoFillActive = false
        _autoFilling.value = false
    }

    fun finishFill() {
        val time = System.currentTimeMillis()
        val record = EquilHistoryRecord(
            id = time,
            type = EquilHistoryRecord.EventType.FILL,
            timestamp = time,
            serialNumber = equilPumpPlugin.serialNumber(),
            resolvedAt = System.currentTimeMillis(),
            resolvedStatus = ResolvedResult.SUCCESS
        )
        equilPumpPlugin.handler?.post {
            equilHistoryRecordDao.insert(record)
        }
        moveStep(EquilWizardStep.ATTACH)
    }

    private fun fillSetStep() {
        aapsLogger.debug(LTag.PUMPCOMM, "fillSetStep: step=$fillStepCount, increment=${EquilConst.EQUIL_STEP_FILL}")
        commandQueue.customCommand(
            CmdStepSet(false, EquilConst.EQUIL_STEP_FILL, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    aapsLogger.debug(LTag.PUMPCOMM, "fillSetStep result=${result.success}")
                    if (result.success) {
                        fillStepCount += EquilConst.EQUIL_STEP_FILL
                        aapsLogger.debug(LTag.PUMPCOMM, "fillSetStep: moved to $fillStepCount")
                        fillReadStatus()
                    } else {
                        aapsLogger.warn(LTag.PUMPCOMM, "fillSetStep FAILED at $fillStepCount")
                        autoFillActive = false
                        _autoFilling.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun fillReadStatus() {
        aapsLogger.debug(LTag.PUMPCOMM, "fillReadStatus: checking resistance at step $fillStepCount")
        commandQueue.customCommand(
            CmdResistanceGet(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    aapsLogger.debug(LTag.PUMPCOMM, "fillReadStatus result=${result.success}, enacted=${result.enacted}")
                    if (result.success) {
                        if (!result.enacted) {
                            // Pin not at piston yet
                            if (autoFillActive) {
                                if (fillStepCount > EquilConst.EQUIL_STEP_MAX) {
                                    aapsLogger.error(LTag.PUMPCOMM, "fillReadStatus: MAX STEP EXCEEDED $fillStepCount")
                                    autoFillActive = false
                                    _autoFilling.value = false
                                    _errorMessage.value = rh.gs(R.string.equil_replace_reservoir)
                                    return
                                }
                                fillSetStep()
                            }
                            // Manual mode: just stop, user taps fill again
                        } else {
                            // Pin reached piston
                            aapsLogger.info(LTag.PUMPCOMM, "fillReadStatus: piston reached at step $fillStepCount")
                            autoFillActive = false
                            _autoFilling.value = false
                            _fillComplete.value = true
                        }
                    } else {
                        aapsLogger.warn(LTag.PUMPCOMM, "fillReadStatus FAILED at $fillStepCount")
                        autoFillActive = false
                        _autoFilling.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    // endregion

    // region Air step

    fun startAirRemoval() {
        _isLoading.value = true
        _errorMessage.value = null
        commandQueue.customCommand(
            CmdStepSet(false, EquilConst.EQUIL_STEP_AIR, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    if (result.success) {
                        _airRemovalDone.value = true
                    } else {
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    fun finishAirStep() {
        _isLoading.value = true
        _errorMessage.value = null
        if (_workflow.value == EquilWorkflow.PAIR) {
            setAlarmMode()
        } else {
            // CHANGE_INSULIN: check if basal schedule needs setting
            if (equilManager.equilState?.basalSchedule == null) setProfile()
            else setTime()
        }
    }

    private fun setAlarmMode() {
        val mode = preferences.get(EquilIntPreferenceKey.EquilTone)
        commandQueue.customCommand(
            CmdAlarmSet(mode, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            setProfile()
                        }
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun setProfile() {
        val profile = profileFunction.getProfile()
        if (profile == null) {
            setTime()
            return
        }
        val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        if (basalSchedule.getEntries().size < 24) {
            setTime()
            return
        }
        commandQueue.customCommand(
            CmdBasalSet(basalSchedule, profile, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        equilManager.setBasalSchedule(basalSchedule)
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            setTime()
                        }
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun setTime() {
        commandQueue.customCommand(
            CmdTimeSet(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            if (_workflow.value == EquilWorkflow.PAIR) readFirmware()
                            else {
                                _isLoading.value = false
                                moveStep(EquilWizardStep.CONFIRM)
                            }
                        }
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun readFirmware() {
        commandQueue.customCommand(
            CmdDevicesGet(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    if (result.success) {
                        moveStep(EquilWizardStep.CONFIRM)
                    } else {
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    // endregion

    // region Confirm step

    fun startConfirm() {
        _isLoading.value = true
        _errorMessage.value = null
        getCurrentInsulin()
    }

    private fun getCurrentInsulin() {
        commandQueue.customCommand(
            CmdInsulinGet(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            setModel()
                        }
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun setModel() {
        commandQueue.customCommand(
            CmdModelSet(RunMode.RUN.command, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    if (result.success) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            setLimits()
                        }
                    } else {
                        _isLoading.value = false
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun setLimits() {
        val profile = pumpSync.expectedPumpState().profile ?: return
        commandQueue.customCommand(
            CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), constraintsChecker.getMaxBasalAllowed(profile).value(), aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    if (result.success) {
                        equilManager.setRunMode(RunMode.RUN)
                        saveActivation()
                    } else {
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    private fun saveActivation() {
        if (_workflow.value == EquilWorkflow.PAIR) {
            pumpSync.connectNewPump()
            pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp = System.currentTimeMillis(),
                type = TE.Type.CANNULA_CHANGE,
                pumpType = PumpType.EQUIL,
                pumpSerial = equilPumpPlugin.serialNumber()
            )
            pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp = System.currentTimeMillis(),
                type = TE.Type.INSULIN_CHANGE,
                pumpType = PumpType.EQUIL,
                pumpSerial = equilPumpPlugin.serialNumber()
            )
        }
        val time = System.currentTimeMillis()
        val record = EquilHistoryRecord(
            id = time,
            type = EquilHistoryRecord.EventType.INSERT_CANNULA,
            timestamp = time,
            serialNumber = equilPumpPlugin.serialNumber(),
            resolvedAt = System.currentTimeMillis(),
            resolvedStatus = ResolvedResult.SUCCESS
        )
        equilPumpPlugin.handler?.post {
            equilHistoryRecordDao.insert(record)
        }
        equilManager.setLastDataTime(System.currentTimeMillis())
        equilManager.setActivationProgress(ActivationProgress.COMPLETED)
        viewModelScope.launch { _events.emit(EquilWizardEvent.Finish) }
    }

    // endregion

    // region Change Insulin step

    fun startChangeInsulin() {
        _isLoading.value = true
        _errorMessage.value = null
        commandQueue.customCommand(
            CmdInsulinChange(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    if (result.success) {
                        equilPumpPlugin.resetData()
                        equilManager.setRunMode(RunMode.STOP)
                        equilManager.setActivationProgress(ActivationProgress.CANNULA_CHANGE)
                        moveStep(EquilWizardStep.ASSEMBLE)
                    } else {
                        _errorMessage.value = rh.gs(R.string.equil_error)
                    }
                }
            }
        )
    }

    // endregion

    // region Unpair steps

    fun startUnpairDetach() {
        _isLoading.value = true
        _errorMessage.value = null
        commandQueue.customCommand(
            CmdInsulinChange(aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    if (result.success) {
                        equilManager.setRunMode(RunMode.STOP)
                        equilPumpPlugin.resetData()
                        equilManager.setActivationProgress(ActivationProgress.CANNULA_CHANGE)
                        moveStep(EquilWizardStep.UNPAIR_CONFIRM)
                    } else {
                        // Still proceed but warn — pump may already be physically detached
                        equilManager.setRunMode(RunMode.STOP)
                        equilPumpPlugin.resetData()
                        equilManager.setActivationProgress(ActivationProgress.CANNULA_CHANGE)
                        _errorMessage.value = rh.gs(R.string.equil_error)
                        moveStep(EquilWizardStep.UNPAIR_CONFIRM)
                    }
                }
            }
        )
    }

    fun confirmUnpair() {
        val name = equilManager.equilState?.serialNumber ?: return
        _isLoading.value = true
        _errorMessage.value = null
        commandQueue.customCommand(
            CmdUnPair(name, preferences.get(EquilStringKey.PairPassword), aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    _isLoading.value = false
                    val message = if (!result.success) rh.gs(R.string.equil_removed_anyway) else rh.gs(R.string.equil_device_unpaired)
                    equilManager.setSerialNumber("")
                    equilManager.setAddress("")
                    rxBus.send(EventEquilUnPairChanged())
                    equilPumpPlugin.clearData()
                    _serialNumberDisplay.value = ""
                    _unpairResultMessage.value = message
                }
            }
        )
    }

    fun dismissUnpairResult() {
        _unpairResultMessage.value = null
        viewModelScope.launch { _events.emit(EquilWizardEvent.Finish) }
    }

    private val _serialNumberDisplay = MutableStateFlow(equilManager.equilState?.serialNumber ?: "")
    val serialNumberDisplay: StateFlow<String> = _serialNumberDisplay.asStateFlow()

    fun getSerialNumber(): String = equilManager.equilState?.serialNumber ?: ""

    // endregion

    // region Attach step

    fun getAttachNextStep(): EquilWizardStep =
        when (_workflow.value) {
            EquilWorkflow.PAIR           -> EquilWizardStep.AIR
            EquilWorkflow.CHANGE_INSULIN -> EquilWizardStep.AIR
            EquilWorkflow.UNPAIR         -> error("Attach step is unreachable in UNPAIR workflow")
        }

    // endregion

    override fun onCleared() {
        super.onCleared()
        stopBLEScan()
        scanHandler.removeCallbacksAndMessages(null)
        scanHandler.looper.quitSafely()
        equilPumpPlugin.tempActivationProgress = ActivationProgress.NONE
    }

    companion object {

        private const val SCAN_PERIOD_MILLIS: Long = 15000
    }
}
