package app.aaps.pump.carelevo.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.pump.ProfileGateStepHost
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.core.ui.compose.siteRotation.SiteLocationStepHost
import app.aaps.pump.carelevo.command.CmdDiscard
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.MutableEventFlow
import app.aaps.pump.carelevo.common.asEventFlow
import app.aaps.pump.carelevo.common.model.Event
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.common.model.State
import app.aaps.pump.carelevo.common.model.UiState
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.usecase.patch.CarelevoPatchForceDiscardUseCase
import app.aaps.pump.carelevo.presentation.model.CarelevoConnectEvent
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
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
    private val commandQueue: CommandQueue,
    private val patchForceDiscardUseCase: CarelevoPatchForceDiscardUseCase,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val insulinManager: InsulinManager,
    private val persistenceLayer: PersistenceLayer
) : ViewModel(), ProfileGateStepHost, SiteLocationStepHost {

    private val _page: MutableStateFlow<CarelevoPatchStep> = MutableStateFlow(CarelevoPatchStep.PATCH_START)
    val page = _page.asStateFlow()

    /** Steps actually shown for this run — PROFILE_GATE / SITE_LOCATION are added only when needed. */
    private var workflowSteps: List<CarelevoPatchStep> = listOf(
        CarelevoPatchStep.PATCH_START,
        CarelevoPatchStep.PATCH_CONNECT,
        CarelevoPatchStep.SAFETY_CHECK,
        CarelevoPatchStep.PATCH_ATTACH,
        CarelevoPatchStep.NEEDLE_INSERTION
    )

    private val _totalSteps = MutableStateFlow(workflowSteps.size)
    val totalSteps = _totalSteps.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex = _currentStepIndex.asStateFlow()

    // ProfileGateStepHost state
    private val _availableProfiles = MutableStateFlow<List<String>>(emptyList())
    override val availableProfiles = _availableProfiles.asStateFlow()
    private val _selectedProfile = MutableStateFlow<String?>(null)
    override val selectedProfile = _selectedProfile.asStateFlow()

    // SiteLocationStepHost state
    private val _siteLocation = MutableStateFlow(TE.Location.NONE)
    override val siteLocation = _siteLocation.asStateFlow()
    private val _siteArrow = MutableStateFlow(TE.Arrow.NONE)
    override val siteArrow = _siteArrow.asStateFlow()
    private val _siteRotationEntries = MutableStateFlow<List<TE>>(emptyList())

    // Insulin selection state (for SELECT_INSULIN step)
    private val _availableInsulins = MutableStateFlow<List<ICfg>>(emptyList())
    val availableInsulins = _availableInsulins.asStateFlow()
    private val _selectedInsulin = MutableStateFlow<ICfg?>(null)
    val selectedInsulin = _selectedInsulin.asStateFlow()
    private val _activeInsulinLabel = MutableStateFlow<String?>(null)
    val activeInsulinLabel = _activeInsulinLabel.asStateFlow()

    val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    /** The insulin-selection step is only shown when more than one insulin is configured. */
    val showInsulinStep: Boolean
        get() = _availableInsulins.value.size > 1

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
        _currentStepIndex.value = workflowSteps.indexOf(page).coerceAtLeast(0)
    }

    /** Build the step list for this run and jump to the first (or resumed) step. */
    suspend fun initWorkflow(screenType: CarelevoScreenType) {
        val needsProfileGate = profileFunction.getRequestedProfile() == null
        val siteRotationEnabled = preferences.get(BooleanKey.SiteRotationManagePump)
        loadInsulins()
        val insulinSelection = showInsulinStep
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        carelevoPatch.setSitePlacement(TE.Location.NONE, TE.Arrow.NONE)
        workflowSteps = buildList {
            if (needsProfileGate) add(CarelevoPatchStep.PROFILE_GATE)
            if (insulinSelection) add(CarelevoPatchStep.SELECT_INSULIN)
            add(CarelevoPatchStep.PATCH_START)
            add(CarelevoPatchStep.SET_AMOUNT)
            add(CarelevoPatchStep.PATCH_CONNECT)
            add(CarelevoPatchStep.SAFETY_CHECK)
            if (siteRotationEnabled) add(CarelevoPatchStep.SITE_LOCATION)
            add(CarelevoPatchStep.PATCH_ATTACH)
            add(CarelevoPatchStep.NEEDLE_INSERTION)
        }
        _totalSteps.value = workflowSteps.size
        if (needsProfileGate) loadAvailableProfiles()
        if (siteRotationEnabled) loadSiteRotationEntries()
        val initial = when (screenType) {
            CarelevoScreenType.SAFETY_CHECK     -> CarelevoPatchStep.SAFETY_CHECK
            CarelevoScreenType.NEEDLE_INSERTION -> CarelevoPatchStep.PATCH_ATTACH
            else                                -> workflowSteps.first()
        }
        setPage(initial)
    }

    /** After the safety check, route through the site-location step when it is enabled. */
    fun advanceFromSafetyCheck() {
        setPage(
            if (workflowSteps.contains(CarelevoPatchStep.SITE_LOCATION)) CarelevoPatchStep.SITE_LOCATION
            else CarelevoPatchStep.PATCH_ATTACH
        )
    }

    /** Advance to whatever step actually follows [current] in this run's step list. */
    private fun goToNextStep(current: CarelevoPatchStep) {
        // A step that is not part of this run has index -1; without the guard the +1 would
        // resolve to the first step and silently rewind the wizard.
        val currentIndex = workflowSteps.indexOf(current)
        if (currentIndex < 0) return
        val next = workflowSteps.getOrNull(currentIndex + 1) ?: return
        setPage(next)
    }

    fun setInputInsulin(insulin: Int) {
        _inputInsulin = insulin
    }

    /** Commit the chosen fill amount from the SET_AMOUNT step and continue. */
    fun confirmAmount(amount: Int) {
        setInputInsulin(amount)
        goToNextStep(CarelevoPatchStep.SET_AMOUNT)
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
            is CarelevoConnectEvent.DiscardFailed   -> event
            is CarelevoConnectEvent.ExitFlow        -> event
            else                                    -> CarelevoConnectEvent.NoAction
        }
    }

    private fun setUiState(state: State) {
        viewModelScope.launch {
            _uiState.tryEmit(state)
        }
    }

    fun startPatchDiscardProcess() {
        when (carelevoPatch.patchState.value?.getOrNull()) {
            is PatchState.NotConnectedNotBooting, null -> {
                triggerEvent(CarelevoConnectEvent.DiscardComplete)
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
                        triggerEvent(CarelevoConnectEvent.DiscardComplete)
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
            .doOnError {
                aapsLogger.debug(LTag.PUMPCOMM, "doOnError called : $it")
                setUiState(UiState.Idle)
                triggerEvent(CarelevoConnectEvent.DiscardFailed)
            }
            .subscribeOn(aapsSchedulers.io)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response success")
                        carelevoPatch.discardTeardown()
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardComplete)
                    }

                    is ResponseResult.Error   -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response error : ${response.e}")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }

                    else                      -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "response failed")
                        setUiState(UiState.Idle)
                        triggerEvent(CarelevoConnectEvent.DiscardFailed)
                    }
                }
            }
    }

    // region ProfileGateStepHost

    private suspend fun loadAvailableProfiles() {
        val names = profileRepository.profiles.value.map { it.name }
        _availableProfiles.value = names
        if (_selectedProfile.value !in names) {
            val activeName = profileFunction.getOriginalProfileName()
            _selectedProfile.value = activeName.takeIf { it in names } ?: names.firstOrNull()
        }
    }

    override fun selectProfile(name: String) {
        _selectedProfile.value = name
    }

    override fun activateSelectedProfile() {
        val name = _selectedProfile.value ?: return
        val store = profileRepository.profile.value ?: return
        val iCfg = _selectedInsulin.value ?: insulinManager.insulins.firstOrNull() ?: return
        viewModelScope.launch {
            val result = profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = name,
                durationInMinutes = 0,
                percentage = 100,
                timeShiftInHours = 0,
                timestamp = System.currentTimeMillis(),
                action = Action.PROFILE_SWITCH,
                source = Sources.Carelevo,
                note = null,
                listValues = listOf(ValueWithUnit.SimpleString(name)),
                iCfg = iCfg
            )
            if (result == null) {
                aapsLogger.error(LTag.PUMP, "ProfileGate: createProfileSwitch failed for $name")
            } else {
                goToNextStep(CarelevoPatchStep.PROFILE_GATE)
            }
        }
    }

    override fun cancelGate() {
        exitWizard()
    }

    fun exitWizard() {
        triggerEvent(CarelevoConnectEvent.ExitFlow)
    }

    // endregion

    // region SiteLocationStepHost

    override fun updateSiteLocation(location: TE.Location) {
        _siteLocation.value = location
        carelevoPatch.setSitePlacement(location, _siteArrow.value)
    }

    override fun updateSiteArrow(arrow: TE.Arrow) {
        _siteArrow.value = arrow
        carelevoPatch.setSitePlacement(_siteLocation.value, arrow)
    }

    override fun completeSiteLocation() {
        carelevoPatch.setSitePlacement(_siteLocation.value, _siteArrow.value)
        setPage(CarelevoPatchStep.PATCH_ATTACH)
    }

    override fun skipSiteLocation() {
        _siteLocation.value = TE.Location.NONE
        _siteArrow.value = TE.Arrow.NONE
        carelevoPatch.setSitePlacement(TE.Location.NONE, TE.Arrow.NONE)
        setPage(CarelevoPatchStep.PATCH_ATTACH)
    }

    override fun bodyType(): BodyType =
        BodyType.fromPref(preferences.get(IntKey.SiteRotationUserProfile))

    override fun siteRotationEntries(): List<TE> = _siteRotationEntries.value

    private fun loadSiteRotationEntries() {
        viewModelScope.launch {
            _siteRotationEntries.value = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - T.days(45).msecs(), false
            ).filter { it.type == TE.Type.CANNULA_CHANGE || it.type == TE.Type.SENSOR_CHANGE }
        }
    }

    // endregion

    // region Insulin selection (SELECT_INSULIN step)

    fun selectInsulin(iCfg: ICfg) {
        _selectedInsulin.value = iCfg
    }

    private suspend fun loadInsulins() {
        if (_availableInsulins.value.isNotEmpty()) return
        val insulins = insulinManager.insulins.map { it.deepClone() }
        val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
        _availableInsulins.value = insulins
        _selectedInsulin.value = insulins.find { it.insulinLabel == activeLabel } ?: insulins.firstOrNull()
        _activeInsulinLabel.value = activeLabel
    }

    /** Apply the chosen insulin (if different from the active one) and continue to the fill step. */
    fun advanceFromInsulin() {
        val selected = _selectedInsulin.value
        if (selected == null) {
            goToNextStep(CarelevoPatchStep.SELECT_INSULIN)
            return
        }
        // Commit the insulin/profile switch BEFORE advancing, so later steps never race a
        // still-in-flight profile change.
        viewModelScope.launch {
            val currentLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            if (selected.insulinLabel != currentLabel) {
                profileFunction.createProfileSwitchWithNewInsulin(selected, Sources.Carelevo)
            }
            goToNextStep(CarelevoPatchStep.SELECT_INSULIN)
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
