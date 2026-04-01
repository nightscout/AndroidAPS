package app.aaps.pump.medtrum.compose

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import app.aaps.pump.medtrum.comm.enums.ModelType
import app.aaps.pump.medtrum.encryption.Crypt
import app.aaps.pump.medtrum.keys.MedtrumStringNonKey
import app.aaps.pump.medtrum.services.MedtrumService
import app.aaps.pump.medtrum.util.MedtrumSnUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PatchEvent {
    data object Finish : PatchEvent()
    data class ShowError(val message: String) : PatchEvent()
}

enum class WizardPage {
    PREPARE, SELECT_INSULIN, PRIME, ATTACH, ACTIVATE, SITE_LOCATION, COMPLETE,
    CONFIRM_DEACTIVATE, DEACTIVATING, DEACTIVATE_COMPLETE,
    RETRY_ACTIVATION
}

@HiltViewModel
@Stable
class MedtrumPatchViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val medtrumPlugin: MedtrumPlugin,
    private val commandQueue: CommandQueue,
    val medtrumPump: MedtrumPump,
    private val insulinManager: InsulinManager,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer
) : ViewModel(), SiteLocationStepHost {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)
    private val medtrumService: MedtrumService?
        get() = medtrumPlugin.getService()

    // PatchStep state machine — maps 1:1 to the old MedtrumViewModel
    private val _patchStep = MutableStateFlow<PatchStep?>(null)
    val patchStep: StateFlow<PatchStep?> = _patchStep.asStateFlow()

    // SetupStep — internal pump state
    private val _setupStep = MutableStateFlow(SetupStep.INITIAL)
    val setupStep: StateFlow<SetupStep> = _setupStep.asStateFlow()

    // Title string resource ID
    private val _title = MutableStateFlow(R.string.step_prepare_patch)
    val title: StateFlow<Int> = _title.asStateFlow()

    // Wizard page for progress indicator
    private val _wizardPage = MutableStateFlow(WizardPage.PREPARE)
    val wizardPage: StateFlow<WizardPage> = _wizardPage.asStateFlow()

    // Total steps and current step index for progress indicator
    private val _totalSteps = MutableStateFlow(5)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    /** Whether the current step allows back/cancel navigation (arrow + system back). */
    private val _canGoBack = MutableStateFlow(true)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    /** Dynamic list of wizard pages for the current workflow. Built on reset(). */
    private var wizardPages: List<WizardPage> = emptyList()

    // Insulin selection state
    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins: StateFlow<List<ICfg>> = _availableInsulins.asStateFlow()

    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin: StateFlow<ICfg?> = _selectedInsulin.asStateFlow()

    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel: StateFlow<String?> = _activeInsulinLabel.asStateFlow()

    // Serial number editing state
    private val _snText = MutableStateFlow("")
    val snText: StateFlow<String> = _snText.asStateFlow()

    private val _snValidationErrorResId = MutableStateFlow<Int?>(null)
    val snValidationErrorResId: StateFlow<Int?> = _snValidationErrorResId.asStateFlow()

    val isSnValid: Boolean
        get() {
            val text = _snText.value
            if (text.isEmpty()) return false
            val serial = text.toLongOrNull(radix = 16) ?: return false
            return MedtrumSnUtil().getDeviceTypeFromSerial(serial) != ModelType.INVALID
        }

    /** Whether the insulin change step should be shown (multiple insulins available) */
    val showInsulinStep: Boolean
        get() = _availableInsulins.value.size > 1

    /** Whether the site location step should be shown */
    val showSiteLocationStep: Boolean
        get() = preferences.get(BooleanKey.SiteRotationManagePump)

    // Site location state (for SITE_LOCATION step)
    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation: StateFlow<TE.Location> = _siteLocation.asStateFlow()

    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow: StateFlow<TE.Arrow> = _siteArrow.asStateFlow()

    private var siteRotationEntriesCache: List<TE> = emptyList()

    // One-time events
    private val _events = MutableSharedFlow<PatchEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<PatchEvent> = _events

    private var oldPatchStep: PatchStep? = null
    private var mInitPatchStep: PatchStep? = null
    private var connectRetryCounter = 0

    init {
        scope.launch {
            medtrumPump.connectionStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumPatchViewModel connectionStateFlow: $state")
                if (_patchStep.value != null) {
                    when (state) {
                        ConnectionState.CONNECTED                                 -> {
                            medtrumPump.lastConnection = System.currentTimeMillis()
                        }

                        ConnectionState.DISCONNECTED                              -> {
                            if (_patchStep.value in listOf(
                                    PatchStep.PRIME,
                                    PatchStep.PRIMING,
                                    PatchStep.PRIME_COMPLETE,
                                    PatchStep.ATTACH_PATCH,
                                    PatchStep.ACTIVATE
                                )
                            ) {
                                medtrumService?.connect("Try reconnect from viewModel")
                            }
                            if (_patchStep.value in listOf(PatchStep.PREPARE_PATCH_CONNECT, PatchStep.RETRY_ACTIVATION_CONNECT)) {
                                if (connectRetryCounter < 3) {
                                    connectRetryCounter++
                                    aapsLogger.info(LTag.PUMP, "preparePatchConnect: retry $connectRetryCounter")
                                    medtrumService?.connect("Try reconnect from viewModel")
                                } else {
                                    aapsLogger.info(LTag.PUMP, "preparePatchConnect: failed to connect")
                                    updateSetupStep(SetupStep.ERROR)
                                }
                            }
                        }

                        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                            // Do nothing
                        }
                    }
                }
            }
        }
        scope.launch {
            medtrumPump.pumpStateFlow.collect { state ->
                aapsLogger.debug(LTag.PUMP, "MedtrumPatchViewModel pumpStateFlow: $state")
                if (_patchStep.value != null) {
                    when (state) {
                        MedtrumPumpState.NONE, MedtrumPumpState.IDLE         -> {
                            updateSetupStep(SetupStep.INITIAL)
                        }

                        MedtrumPumpState.FILLED                              -> {
                            updateSetupStep(SetupStep.FILLED)
                        }

                        MedtrumPumpState.PRIMING                             -> {
                            updateSetupStep(SetupStep.PRIMING)
                        }

                        MedtrumPumpState.PRIMED, MedtrumPumpState.EJECTED    -> {
                            updateSetupStep(SetupStep.PRIMED)
                        }

                        MedtrumPumpState.ACTIVE, MedtrumPumpState.ACTIVE_ALT -> {
                            updateSetupStep(SetupStep.ACTIVATED)
                        }

                        MedtrumPumpState.STOPPED                             -> {
                            updateSetupStep(SetupStep.STOPPED)
                        }

                        else                                                 -> {
                            updateSetupStep(SetupStep.ERROR)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun moveStep(newPatchStep: PatchStep) {
        oldPatchStep = _patchStep.value

        if (oldPatchStep != newPatchStep) {
            when (newPatchStep) {
                PatchStep.CANCEL,
                PatchStep.COMPLETE,
                PatchStep.ACTIVATE_COMPLETE,
                PatchStep.START_DEACTIVATION,
                PatchStep.DEACTIVATE,
                PatchStep.FORCE_DEACTIVATION,
                PatchStep.DEACTIVATION_COMPLETE,
                PatchStep.PREPARE_PATCH,
                PatchStep.SELECT_INSULIN,
                PatchStep.SITE_LOCATION,
                PatchStep.RETRY_ACTIVATION      -> {
                    // No connection required
                }

                PatchStep.RETRY_ACTIVATION_CONNECT,
                PatchStep.PREPARE_PATCH_CONNECT -> {
                    if (medtrumService?.isConnected == true) {
                        aapsLogger.info(LTag.PUMP, "moveStep: connected, not moving step")
                        return
                    }
                }

                PatchStep.PRIME,
                PatchStep.PRIMING,
                PatchStep.PRIME_COMPLETE,
                PatchStep.ATTACH_PATCH,
                PatchStep.ACTIVATE              -> {
                    if (medtrumService?.isConnected == false) {
                        aapsLogger.info(LTag.PUMP, "moveStep: not connected, not moving step")
                        return
                    }
                }
            }
        }

        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "moveStep: $oldPatchStep -> $newPatchStep")
    }

    fun forceMoveStep(newPatchStep: PatchStep) {
        val oldStep = _patchStep.value
        prepareStep(newPatchStep)
        aapsLogger.info(LTag.PUMP, "forceMoveStep: $oldStep -> $newPatchStep")
    }

    /** Called by WizardScreen on back arrow press or cancel dialog confirmation. */
    fun handleBack() {
        when (_patchStep.value) {
            PatchStep.COMPLETE,
            PatchStep.DEACTIVATION_COMPLETE -> handleComplete()

            else                            -> handleCancel()
        }
    }

    fun handleCancel() {
        if (oldPatchStep !in listOf(
                PatchStep.PREPARE_PATCH,
                PatchStep.START_DEACTIVATION,
                PatchStep.DEACTIVATE,
                PatchStep.FORCE_DEACTIVATION,
                PatchStep.DEACTIVATION_COMPLETE
            )
        ) {
            medtrumService?.disconnect("Cancel")
        }
        if (oldPatchStep == PatchStep.RETRY_ACTIVATION_CONNECT) {
            scope.launch {
                while (medtrumService?.isConnecting == true || medtrumService?.isConnected == true) {
                    delay(100)
                }
                medtrumPump.pumpState = MedtrumPumpState.FILLED
                _events.tryEmit(PatchEvent.Finish)
            }
            return
        }
        _events.tryEmit(PatchEvent.Finish)
    }

    fun handleComplete() {
        medtrumService?.disconnect("Complete")
        _events.tryEmit(PatchEvent.Finish)
    }

    fun reset() {
        aapsLogger.info(LTag.PUMP, "reset: clearing state for new workflow session")
        _patchStep.value = null
        _setupStep.value = SetupStep.INITIAL
        _title.value = R.string.step_prepare_patch
        _wizardPage.value = WizardPage.PREPARE
        wizardPages = emptyList()
        _totalSteps.value = 5
        _currentStepIndex.value = 0
        _canGoBack.value = true
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        siteRotationEntriesCache = emptyList()
        oldPatchStep = null
        mInitPatchStep = null
        connectRetryCounter = 0
        if (showSiteLocationStep) loadSiteRotationEntries()
    }

    fun initializePatchStep(step: PatchStep) {
        aapsLogger.info(LTag.PUMP, "initializePatchStep: $step")
        loadInsulins()
        wizardPages = buildWizardPages(step)
        mInitPatchStep = prepareStep(step)
    }

    fun preparePatch() {
        medtrumService?.disconnect("PreparePatch")
    }

    fun preparePatchConnect() {
        scope.launch {
            if (medtrumService?.isConnected == false) {
                aapsLogger.info(LTag.PUMP, "preparePatch: new session")
                medtrumPump.patchSessionToken = Crypt().generateRandomToken()
                medtrumService?.connect("PreparePatch")
            } else {
                aapsLogger.error(LTag.PUMP, "preparePatch: Already connected when trying to prepare patch")
            }
        }
    }

    fun startPrime() {
        scope.launch {
            if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
                aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
            } else {
                if (medtrumService?.startPrime() == true) {
                    aapsLogger.info(LTag.PUMP, "startPrime: success!")
                } else {
                    aapsLogger.info(LTag.PUMP, "startPrime: failure!")
                    updateSetupStep(SetupStep.ERROR)
                }
            }
        }
    }

    fun startActivate() {
        scope.launch {
            if (medtrumService?.startActivate() == true) {
                aapsLogger.info(LTag.PUMP, "startActivate: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startActivate: failure!")
                updateSetupStep(SetupStep.ERROR)
            }
        }
    }

    fun deactivatePatch() {
        commandQueue.deactivate(object : Callback() {
            override fun run() {
                if (this.result.success) {
                    // State change will handle navigation
                } else {
                    if (medtrumPump.pumpState >= MedtrumPumpState.OCCLUSION && medtrumPump.pumpState <= MedtrumPumpState.NO_CALIBRATION) {
                        aapsLogger.info(LTag.PUMP, "deactivatePatch: force deactivation")
                        medtrumService?.disconnect("ForceDeactivation")
                        SystemClock.sleep(1000)
                        medtrumPump.pumpState = MedtrumPumpState.STOPPED
                    } else {
                        aapsLogger.info(LTag.PUMP, "deactivatePatch: failure!")
                        updateSetupStep(SetupStep.ERROR)
                    }
                }
            }
        })
    }

    fun retryActivationConnect() {
        scope.launch {
            if (medtrumService?.isConnected == false) {
                medtrumPump.pumpState = MedtrumPumpState.NONE
                medtrumService?.connect("RetryActivationConnect")
            } else {
                aapsLogger.error(LTag.PUMP, "retryActivationConnect: Already connected")
                updateSetupStep(SetupStep.ERROR)
            }
        }
    }

    fun loadInsulins() {
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

    fun initSnText() {
        val currentSn = medtrumPump.pumpSN
        _snText.value = if (currentSn != 0L) currentSn.toString(radix = 16).uppercase() else ""
        _snValidationErrorResId.value = null
    }

    fun updateSnText(newText: String) {
        // Filter to hex chars only and uppercase
        val filtered = newText.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }.uppercase()
        _snText.value = filtered
        _snValidationErrorResId.value = validateSn(filtered)
    }

    fun saveSn() {
        val text = _snText.value
        if (!isSnValid) return
        preferences.put(MedtrumStringNonKey.SnInput, text)
        medtrumPump.loadUserSettingsFromSP()
    }

    private fun validateSn(text: String): Int? {
        if (text.isEmpty()) return null
        val serial = text.toLongOrNull(radix = 16) ?: return R.string.sn_input_invalid
        val deviceType = MedtrumSnUtil().getDeviceTypeFromSerial(serial)
        return if (deviceType == ModelType.INVALID) R.string.sn_input_invalid else null
    }

    // region SiteLocationStepHost

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
    }

    override fun completeSiteLocation() {
        // Site location is saved after activation completes (patchStartTime not available yet)
        moveStep(PatchStep.ACTIVATE)
    }

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        moveStep(PatchStep.ACTIVATE)
    }

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    override fun siteRotationEntries(): List<TE> = siteRotationEntriesCache

    private fun loadSiteRotationEntries() {
        scope.launch {
            siteRotationEntriesCache = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - T.days(45).msecs(), false
            ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
        }
    }

    /** Save site location/arrow to the CANNULA_CHANGE therapy event created during activation. */
    private fun saveSiteLocationToTherapyEvent(activationTimestamp: Long) {
        val location = _siteLocation.value.takeIf { it != TE.Location.NONE }
        val arrow = _siteArrow.value.takeIf { it != TE.Arrow.NONE }
        if (location != null || arrow != null) {
            scope.launch {
                try {
                    val entries = persistenceLayer.getTherapyEventDataFromToTime(activationTimestamp, activationTimestamp)
                        .filter { it.type == TE.Type.CANNULA_CHANGE }
                    entries.firstOrNull()?.let { te ->
                        persistenceLayer.insertOrUpdateTherapyEvent(te.copy(location = location, arrow = arrow))
                    }
                } catch (_: Exception) {
                    // location is optional
                }
            }
        }
    }

    // endregion

    /** Navigate to the step after activation completes. Save site location if selected. */
    fun moveToPostActivationStep() {
        if (showSiteLocationStep && _siteLocation.value != TE.Location.NONE) {
            saveSiteLocationToTherapyEvent(medtrumPump.patchStartTime)
        }
        moveStep(PatchStep.COMPLETE)
    }

    /** Execute profile switch if user selected a different insulin. Called after activation completes. */
    fun executeInsulinProfileSwitch() {
        val selected = _selectedInsulin.value ?: return
        val activeLabel = _activeInsulinLabel.value
        if (selected.insulinLabel == activeLabel) return
        viewModelScope.launch {
            profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.Medtrum)
        }
    }

    private fun prepareStep(newStep: PatchStep): PatchStep {
        val stringResId = when (newStep) {
            PatchStep.PREPARE_PATCH            -> R.string.step_prepare_patch
            PatchStep.PREPARE_PATCH_CONNECT    -> R.string.step_prepare_patch_connect
            PatchStep.SELECT_INSULIN           -> R.string.step_select_insulin
            PatchStep.PRIME                    -> R.string.step_prime
            PatchStep.PRIMING                  -> R.string.step_priming
            PatchStep.PRIME_COMPLETE           -> R.string.step_priming_complete
            PatchStep.ATTACH_PATCH             -> R.string.step_attach
            PatchStep.ACTIVATE                 -> R.string.step_activate
            PatchStep.ACTIVATE_COMPLETE        -> R.string.step_activate_complete
            PatchStep.SITE_LOCATION            -> app.aaps.core.ui.R.string.site_rotation
            PatchStep.START_DEACTIVATION       -> R.string.step_deactivate
            PatchStep.DEACTIVATE               -> R.string.step_deactivating
            PatchStep.DEACTIVATION_COMPLETE    -> R.string.step_deactivate_complete
            PatchStep.RETRY_ACTIVATION,
            PatchStep.RETRY_ACTIVATION_CONNECT -> R.string.step_retry_activation

            PatchStep.COMPLETE,
            PatchStep.FORCE_DEACTIVATION,
            PatchStep.CANCEL                   -> _title.value
        }

        if (_title.value != stringResId) {
            aapsLogger.info(LTag.PUMP, "prepareStep: title: $stringResId")
            _title.value = stringResId
        }

        _patchStep.value = newStep
        _canGoBack.value = newStep in listOf(
            PatchStep.PREPARE_PATCH,
            PatchStep.SELECT_INSULIN,
            PatchStep.SITE_LOCATION,
            PatchStep.START_DEACTIVATION,
            PatchStep.RETRY_ACTIVATION,
            PatchStep.COMPLETE,
            PatchStep.DEACTIVATION_COMPLETE
        )
        updateWizardPage(newStep)

        // Handle immediate transitions (replicate MedtrumActivity behavior)
        when (newStep) {
            PatchStep.FORCE_DEACTIVATION -> {
                medtrumPump.pumpState = MedtrumPumpState.STOPPED
                moveStep(PatchStep.DEACTIVATION_COMPLETE)
            }

            PatchStep.CANCEL             -> handleCancel()
            PatchStep.COMPLETE           -> handleComplete()

            else                         -> { /* normal step, handled by UI */
            }
        }

        return newStep
    }

    fun updateSetupStep(newSetupStep: SetupStep) {
        aapsLogger.info(LTag.PUMP, "curSetupStep: ${_setupStep.value}, newSetupStep: $newSetupStep")
        _setupStep.value = newSetupStep
    }

    /** Build the dynamic wizard page list for the given start step. */
    private fun buildWizardPages(startStep: PatchStep): List<WizardPage> = when (startStep) {
        PatchStep.PREPARE_PATCH      -> buildList {
            add(WizardPage.PREPARE)
            if (showInsulinStep) add(WizardPage.SELECT_INSULIN)
            add(WizardPage.PRIME)
            add(WizardPage.ATTACH)
            if (showSiteLocationStep) add(WizardPage.SITE_LOCATION)
            add(WizardPage.ACTIVATE)
            add(WizardPage.COMPLETE)
        }

        PatchStep.START_DEACTIVATION -> listOf(
            WizardPage.CONFIRM_DEACTIVATE,
            WizardPage.DEACTIVATING,
            WizardPage.DEACTIVATE_COMPLETE
        )

        PatchStep.RETRY_ACTIVATION   -> listOf(WizardPage.RETRY_ACTIVATION)

        else                         -> wizardPages // keep current
    }

    /** Map a PatchStep (which may have sub-states) to its WizardPage. */
    private fun PatchStep.toWizardPage(): WizardPage? = when (this) {
        PatchStep.PREPARE_PATCH,
        PatchStep.PREPARE_PATCH_CONNECT    -> WizardPage.PREPARE

        PatchStep.SELECT_INSULIN           -> WizardPage.SELECT_INSULIN
        PatchStep.PRIME,
        PatchStep.PRIMING,
        PatchStep.PRIME_COMPLETE           -> WizardPage.PRIME

        PatchStep.ATTACH_PATCH             -> WizardPage.ATTACH
        PatchStep.ACTIVATE,
        PatchStep.ACTIVATE_COMPLETE        -> WizardPage.ACTIVATE

        PatchStep.SITE_LOCATION            -> WizardPage.SITE_LOCATION
        PatchStep.COMPLETE                 -> WizardPage.COMPLETE
        PatchStep.START_DEACTIVATION       -> WizardPage.CONFIRM_DEACTIVATE
        PatchStep.DEACTIVATE,
        PatchStep.FORCE_DEACTIVATION       -> WizardPage.DEACTIVATING

        PatchStep.DEACTIVATION_COMPLETE    -> WizardPage.DEACTIVATE_COMPLETE
        PatchStep.RETRY_ACTIVATION,
        PatchStep.RETRY_ACTIVATION_CONNECT -> WizardPage.RETRY_ACTIVATION

        PatchStep.CANCEL                   -> null
    }

    private fun updateWizardPage(step: PatchStep) {
        val page = step.toWizardPage() ?: return // CANCEL keeps current values
        val index = wizardPages.indexOf(page).coerceAtLeast(0)
        _wizardPage.value = page
        _totalSteps.value = wizardPages.size
        _currentStepIndex.value = index
    }

    enum class SetupStep {
        INITIAL, FILLED, PRIMING, PRIMED, ACTIVATED, ERROR, START_DEACTIVATION, STOPPED
    }
}
