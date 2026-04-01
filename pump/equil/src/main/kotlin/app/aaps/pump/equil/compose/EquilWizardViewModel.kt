package app.aaps.pump.equil.compose

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.ble.EquilBleTransport
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
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class EquilWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val commandQueue: CommandQueue,
    private val equilPumpPlugin: EquilPumpPlugin,
    private val equilManager: EquilManager,
    private val pumpSync: PumpSync,
    private val persistenceLayer: PersistenceLayer,
    private val equilHistoryRecordDao: EquilHistoryRecordDao,
    private val constraintsChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val rxBus: RxBus,
    private val insulinManager: InsulinManager,
    private val bleTransport: EquilBleTransport,
    private val hardLimits: HardLimits
) : ViewModel(), SiteLocationStepHost {

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

    // Site location state (for SITE_LOCATION step)
    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation: StateFlow<TE.Location> = _siteLocation.asStateFlow()

    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow: StateFlow<TE.Arrow> = _siteArrow.asStateFlow()

    private var activationTimestamp: Long = 0L

    // Insulin selection state
    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins: StateFlow<List<ICfg>> = _availableInsulins.asStateFlow()

    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin: StateFlow<ICfg?> = _selectedInsulin.asStateFlow()

    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel: StateFlow<String?> = _activeInsulinLabel.asStateFlow()

    /** Whether the insulin selection step should be shown (multiple insulins available) */
    val showInsulinStep: Boolean
        get() = _availableInsulins.value.size > 1

    val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    // Events
    private val _events = MutableSharedFlow<EquilWizardEvent>()
    val events: SharedFlow<EquilWizardEvent> = _events.asSharedFlow()

    // endregion

    // region BLE scan internals
    private var isScanActive = false
    private var scanJob: kotlinx.coroutines.Job? = null
    private val scanHandler = Handler(HandlerThread("EquilScanHandler").also { it.start() }.looper)
    private var serialNumber = ""
    private var password = ""

    // Device scan step state
    private val _scannedDevices = MutableStateFlow<List<app.aaps.core.interfaces.pump.ble.ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<app.aaps.core.interfaces.pump.ble.ScannedDevice>> = _scannedDevices.asStateFlow()
    private val _selectedDeviceName = MutableStateFlow("")
    val selectedDeviceName: StateFlow<String> = _selectedDeviceName.asStateFlow()
    private var selectedDeviceAddress = ""
    @Volatile private var fillStepCount = 0
    @Volatile private var autoFillActive = false
    // endregion

    sealed class EquilWizardEvent {
        data object Finish : EquilWizardEvent()
        data class ShowMessage(val message: String) : EquilWizardEvent()
    }

    // region Initialization

    private var workflowSteps: List<EquilWizardStep> = emptyList()

    fun initializeWorkflow(workflow: EquilWorkflow) {
        _workflow.value = workflow
        loadInsulins()
        val siteRotationEnabled = preferences.get(BooleanKey.SiteRotationManagePump)
        workflowSteps = workflow.steps(siteRotationEnabled, insulinSelectionEnabled = showInsulinStep)
        _totalSteps.value = workflowSteps.size
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

        viewModelScope.launch {
            siteRotationEntriesCache = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - T.days(45).msecs(), false
            ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
        }
        // Note: cache loads async but site location step is never the first step,
        // so the data will be available by the time the user reaches it.
        moveStep(workflowSteps.first())
    }

    /** Move to the step that follows [currentStep] in the workflow. */
    fun moveToNextStep(currentStep: EquilWizardStep) {
        val index = workflowSteps.indexOf(currentStep)
        if (index >= 0 && index < workflowSteps.lastIndex) {
            moveStep(workflowSteps[index + 1])
        }
    }

    /** Move to the step before [currentStep] in the workflow. */
    fun moveToPreviousStep(currentStep: EquilWizardStep) {
        val index = workflowSteps.indexOf(currentStep)
        if (index > 0) {
            moveStep(workflowSteps[index - 1])
        }
    }

    /** Whether [step] has a predecessor in the workflow it can go back to. */
    fun hasPreviousStep(step: EquilWizardStep): Boolean =
        workflowSteps.indexOf(step) > 0

    // endregion

    // region Navigation

    fun moveStep(step: EquilWizardStep) {
        _errorMessage.value = null
        _isLoading.value = false
        _wizardStep.value = step
        updateStepProgress(step)
    }

    private fun updateStepProgress(step: EquilWizardStep) {
        val index = workflowSteps.indexOf(step).coerceAtLeast(0)
        _currentStepIndex.value = index
        // CHANGE_INSULIN workflow uses different title for ASSEMBLE step
        val titleOverride = if (_workflow.value == EquilWorkflow.CHANGE_INSULIN && step == EquilWizardStep.ASSEMBLE)
            R.string.equil_title_dressing
        else
            step.titleResId
        _titleResId.value = titleOverride
    }

    fun handleCancel() {
        stopBLEScan()
        autoFillActive = false
        _autoFilling.value = false
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

    fun getAssembleNextStep(): EquilWizardStep {
        val index = workflowSteps.indexOf(EquilWizardStep.ASSEMBLE)
        return workflowSteps[index + 1]
    }

    // endregion

    // region Serial Number / BLE Scan step

    fun prepareBLEScan(): Boolean {
        bleTransport.adapter.enable()
        return true
    }

    fun startBLEScan(serial: String, pwd: String) {
        serialNumber = serial
        password = pwd
        preferences.put(EquilStringKey.PairPassword, password)
        equilManager.setAddress("")
        equilManager.setSerialNumber("")

        isScanActive = true
        _scanning.value = true
        _scanError.value = null

        scanHandler.postDelayed(scanTimeoutRunnable, SCAN_PERIOD_MILLIS)

        bleTransport.scanAddress = null // scan for all Equil devices
        scanJob = viewModelScope.launch {
            bleTransport.scanner.scannedDevices.collect { device ->
                aapsLogger.debug(LTag.PUMPBTCOMM, "BLE scan result: ${device.name} / ${device.address}")
                if (device.name.contains(serialNumber)) {
                    device.scanRecordBytes?.let {
                        if (it.size > 24) {
                            val historyIndex = Utils.bytesToInt(it[24], it[23])
                            equilManager.setStartHistoryIndex(historyIndex)
                            aapsLogger.debug(LTag.PUMPCOMM, "historyIndex $historyIndex")
                        }
                    }
                    scanHandler.removeCallbacks(scanTimeoutRunnable)
                    stopBLEScan()
                    getVersion(device.name, device.address)
                }
            }
        }

        bleTransport.scanner.startScan()
        aapsLogger.debug(LTag.PUMPCOMM, "startBLEScan: Scanning Start for $serial")
    }

    fun stopBLEScan() {
        if (isScanActive) {
            isScanActive = false
            _scanning.value = false
            scanHandler.removeCallbacks(scanTimeoutRunnable)
            scanJob?.cancel()
            scanJob = null
            bleTransport.scanner.stopScan()
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

    // --- New scan-first flow ---

    fun startDeviceScan() {
        _scannedDevices.value = emptyList()
        bleTransport.scanAddress = null
        scanJob = viewModelScope.launch {
            bleTransport.scanner.scannedDevices.collect { device ->
                aapsLogger.debug(LTag.PUMPBTCOMM, "Device scan result: ${device.name} / ${device.address}")
                val current = _scannedDevices.value
                if (current.none { it.address == device.address }) {
                    _scannedDevices.value = current + device
                }
            }
        }
        bleTransport.scanner.startScan()
    }

    fun stopDeviceScan() {
        scanJob?.cancel()
        scanJob = null
        bleTransport.scanner.stopScan()
    }

    fun onDeviceSelected(device: app.aaps.core.interfaces.pump.ble.ScannedDevice) {
        stopDeviceScan()
        _selectedDeviceName.value = device.name
        selectedDeviceAddress = device.address
        // Extract serial from BLE name (e.g. "Equil - A12345" → "A12345")
        serialNumber = device.name.replace("Equil - ", "").trim()
        device.scanRecordBytes?.let {
            if (it.size > 24) {
                val historyIndex = Utils.bytesToInt(it[24], it[23])
                equilManager.setStartHistoryIndex(historyIndex)
                aapsLogger.debug(LTag.PUMPCOMM, "historyIndex $historyIndex")
            }
        }
        moveStep(EquilWizardStep.PASSWORD)
    }

    fun startPairing(pwd: String) {
        password = pwd
        preferences.put(EquilStringKey.PairPassword, password)
        equilManager.setAddress("")
        equilManager.setSerialNumber("")

        _scanning.value = true
        _scanError.value = null

        getVersion(_selectedDeviceName.value, selectedDeviceAddress)
    }

    // --- End new scan-first flow ---

    private fun getVersion(deviceName: String, deviceAddress: String) {
        val cmdDevicesOldGet = CmdDevicesOldGet(deviceAddress, aapsLogger, preferences, equilManager)
        commandQueue.customCommand(cmdDevicesOldGet, object : Callback() {
            override fun run() {
                aapsLogger.debug(LTag.PUMPCOMM, "getVersion result=${result.success}")
                if (result.success) {
                    if (cmdDevicesOldGet.isSupport(serialNumber)) {
                        viewModelScope.launch {
                            delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                            pair(deviceName, deviceAddress)
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

    private fun pair(deviceName: String, deviceAddress: String) {
        equilManager.setActivationProgress(ActivationProgress.PRIMING)
        equilManager.setBluetoothConnectionState(BluetoothConnectionState.CONNECTED)
        aapsLogger.debug(LTag.PUMPCOMM, "pair: $deviceName / $deviceAddress")
        commandQueue.customCommand(
            CmdPair(deviceName, deviceAddress, password, aapsLogger, preferences, equilManager),
            object : Callback() {
                override fun run() {
                    aapsLogger.debug(LTag.PUMPCOMM, "pair result=${result.success}, enacted=${result.enacted}")
                    if (result.success) {
                        if (result.enacted) {
                            viewModelScope.launch {
                                delay(EquilConst.EQUIL_BLE_NEXT_CMD)
                                pumpSettings(deviceAddress, deviceName)
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
        val profile = runBlocking { pumpSync.expectedPumpState() }.profile
        val maxBasal = if (profile != null) constraintsChecker.getMaxBasalAllowed(profile).value() else hardLimits.maxBasal()
        commandQueue.customCommand(
            CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), maxBasal, aapsLogger, preferences, equilManager),
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
        viewModelScope.launch {
            val profile = profileFunction.getProfile()
            if (profile == null) {
                setTime()
                return@launch
            }
            val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
            if (basalSchedule.getEntries().size < 24) {
                setTime()
                return@launch
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
                                moveToNextStep(EquilWizardStep.AIR)
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
                        moveToNextStep(EquilWizardStep.AIR)
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
        val profile = runBlocking { pumpSync.expectedPumpState() }.profile
        val maxBasal = if (profile != null) constraintsChecker.getMaxBasalAllowed(profile).value() else hardLimits.maxBasal()
        commandQueue.customCommand(
            CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), maxBasal, aapsLogger, preferences, equilManager),
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
        activationTimestamp = System.currentTimeMillis()
        executeInsulinProfileSwitch()
        if (_workflow.value == EquilWorkflow.PAIR) {
            pumpSync.connectNewPump()
        }
        val time = activationTimestamp
        if (_workflow.value == EquilWorkflow.PAIR || _workflow.value == EquilWorkflow.CHANGE_INSULIN) {
            runBlocking {
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = time,
                    type = TE.Type.CANNULA_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = time,
                    type = TE.Type.INSULIN_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )
            }
        }
        val location = _siteLocation.value.takeIf { it != TE.Location.NONE }
        val arrow = _siteArrow.value.takeIf { it != TE.Arrow.NONE }
        if (location != null || arrow != null) {
            viewModelScope.launch {
                try {
                    val entries = persistenceLayer.getTherapyEventDataFromToTime(time, time)
                        .filter { it.type == TE.Type.CANNULA_CHANGE }
                    entries.firstOrNull()?.let { te ->
                        persistenceLayer.insertOrUpdateTherapyEvent(te.copy(location = location, arrow = arrow))
                    }
                } catch (_: Exception) {
                    // location is optional
                }
            }
        }
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

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
    }

    override fun completeSiteLocation() {
        moveToNextStep(EquilWizardStep.SITE_LOCATION)
    }

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        moveToNextStep(EquilWizardStep.SITE_LOCATION)
    }

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(app.aaps.core.keys.IntKey.SiteRotationUserProfile))

    private var siteRotationEntriesCache: List<TE> = emptyList()

    override fun siteRotationEntries(): List<TE> = siteRotationEntriesCache

    // endregion

    // region Insulin selection

    private fun loadInsulins() {
        if (_availableInsulins.value.isNotEmpty()) return
        viewModelScope.launch {
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            val current = insulins.find { it.insulinLabel == activeLabel } ?: insulins.firstOrNull()
            _availableInsulins.value = insulins
            _selectedInsulin.value = current
            _activeInsulinLabel.value = activeLabel
        }
    }

    fun selectInsulin(iCfg: ICfg) {
        _selectedInsulin.value = iCfg
    }

    /** Execute profile switch if user selected a different insulin. Called after activation completes. */
    fun executeInsulinProfileSwitch() {
        val selected = _selectedInsulin.value ?: return
        val activeLabel = _activeInsulinLabel.value
        if (selected.insulinLabel == activeLabel) return
        viewModelScope.launch {
            profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.Equil)
        }
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

    // endregion

    // region Attach step

    fun getAttachNextStep(): EquilWizardStep {
        val index = workflowSteps.indexOf(EquilWizardStep.ATTACH)
        return workflowSteps[index + 1]
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
