package app.aaps.ui.compose.insulinManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.compensateForClockSkew
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.observeChange
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

@HiltViewModel
class InsulinManagementViewModel @Inject constructor(
    val insulinManager: InsulinManager,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val hardLimits: HardLimits,
    private val uel: UserEntryLogger,
    val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val persistenceLayer: PersistenceLayer,
    private val profileRepository: ProfileRepository,
    private val config: Config,
    private val batchExecutor: BatchExecutor,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsulinManagementUiState())
    val uiState: StateFlow<InsulinManagementUiState> = _uiState.asStateFlow()

    sealed class SideEffect {
        data class ScrollToInsulin(val index: Int) : SideEffect()
        data object NavigateBack : SideEffect()

        /** The MASTER prepared the activation and returned its confirmation [lines]; show them, then [commit] [bolusId]. */
        data class ShowConfirmation(val bolusId: Long, val lines: List<ConfirmationLine>) : SideEffect()
    }

    private val _sideEffect = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sideEffect: SharedFlow<SideEffect> = _sideEffect.asSharedFlow()

    // Last InsulinConfiguration value this VM has accounted for. Lets [onExternalConfigChange] tell
    // our own saves (which also write the pref) apart from external client→master sync pushes.
    private var lastAppliedConfig: String = ""

    // Guards [prepareActivation] against re-taps while a prepare round-trip is still in flight, so a
    // double-tap on Activate can't launch two concurrent prepares.
    private val activating = AtomicBoolean(false)

    init {
        lastAppliedConfig = preferences.get(StringNonKey.InsulinConfiguration)
        loadData()
        observeProfileChanges()
        observeConfigChanges()
    }

    fun setScreenMode(mode: ScreenMode) {
        _uiState.update { it.copy(screenMode = mode) }
        loadData(reload = true)
    }

    fun loadData(targetIndex: Int? = null, reload: Boolean = true, autoName: Boolean = false, saveAfterAutoName: Boolean = false) {
        viewModelScope.launch {
            if (reload) insulinManager.loadSettings()
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeICfg = profileFunction.getProfile()?.iCfg
            val activeLabel = activeICfg?.insulinLabel
            val activeConcentration = activeICfg?.concentration ?: 1.0  // Only insulin with Current Active concentration can be set from Insulin Management
            val currentIndex = (if (reload) insulinManager.insulinIndex(activeICfg) else targetIndex ?: uiState.value.currentCardIndex)
                .coerceIn(0, (insulins.size - 1).coerceAtLeast(0))
            val currentICfg = insulins.getOrNull(currentIndex)
            val template = currentICfg?.let { cfg -> InsulinType.fromPeak(cfg.insulinPeakTime) }
            val defaultNickname = template?.let { rh.gs(it.label) } ?: ""
            val editorNickname = currentICfg?.insulinNickname?.takeIf { it.isNotBlank() } ?: defaultNickname
            val autoNameEnabled = editorNickname == defaultNickname || autoName

            _uiState.update {
                it.copy(
                    insulins = insulins,
                    currentCardIndex = currentIndex,
                    activeInsulinLabel = activeLabel,
                    activeConcentration = activeConcentration,
                    editorNickname = editorNickname,
                    editorTemplate = template,
                    editorConcentration = currentICfg?.let { cfg -> ConcentrationType.fromDouble(cfg.concentration) } ?: ConcentrationType.U100,
                    editorPeakMinutes = currentICfg?.peak ?: 75,
                    editorDiaHours = currentICfg?.dia ?: 5.0,
                    autoNameEnabled = autoNameEnabled,
                    isLoading = false
                )
            }

            if (autoName) {
                autoGenerateName()
                if (saveAfterAutoName) saveCurrentInsulin()
            }
            
            // Force the scroll to the computed currentIndex on reload (entry) or targetIndex request.
            // This ensures we land on the active insulin when opening the screen, even if the VM state was stale.
            _sideEffect.emit(SideEffect.ScrollToInsulin(currentIndex))
        }
    }

    /**
     * Subscribe to profile change events
     */
    private fun observeProfileChanges() {
        // Drop the replayed initial value; only react to subsequent profile-list mutations.
        profileRepository.profiles.drop(1)
            .onEach { updateRunningInsulin() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges<EPS>()
            .compensateForClockSkew(config, dateUtil)
            .onEach { updateRunningInsulin() }.launchIn(viewModelScope)
    }

    private suspend fun updateRunningInsulin() {
        val now = dateUtil.now()
        val activeIcfg = persistenceLayer.getEffectiveProfileSwitchActiveAt(now)?.iCfg
        _uiState.update {
            it.copy(
                activeInsulinLabel = activeIcfg?.insulinLabel,
                activeConcentration = activeIcfg?.concentration ?: 1.0
            )
        }
    }

    /**
     * React to external [StringNonKey.InsulinConfiguration] changes (the master applying a client
     * push, or a client receiving the master's republish). Our own saves are filtered out via
     * [lastAppliedConfig], so this only fires for genuinely external updates.
     */
    private fun observeConfigChanges() {
        preferences.observeChange(StringNonKey.InsulinConfiguration)
            .onEach { onExternalConfigChange() }
            .launchIn(viewModelScope)
    }

    private fun onExternalConfigChange() {
        val incoming = preferences.get(StringNonKey.InsulinConfiguration)
        if (incoming == lastAppliedConfig) return // our own write echoing back
        lastAppliedConfig = incoming
        // reload = true: the apply that triggered this ran insulinManager.loadSettings()/reloadInternalState()
        // on a background (WS) thread, so the in-memory list isn't reliably visible here — re-read the
        // pref via the synchronized loadSettings(). lastAppliedConfig keeps the re-store from looping.
        when {
            // Client defers to the master: adopt the new definitions silently (discards unsaved edits).
            config.AAPSCLIENT   -> loadData(reload = true)
            // Master is the conflict authority: ask before discarding an in-progress edit.
            hasUnsavedChanges() -> _uiState.update { it.copy(externalUpdatePending = true) }
            // Master, no in-progress edit: adopt the external change but keep the user's card selection.
            // loadSettings() re-reads the pref synchronously (we're on the Main viewModelScope collector),
            // then loadData(reload = false) rebuilds state off the fresh list while preserving currentCardIndex.
            else                -> {
                insulinManager.loadSettings()
                loadData(reload = false)
            }
        }
    }

    /** Master chose to load the externally-changed definitions, discarding the unsaved edit. */
    fun acceptExternalUpdate() {
        _uiState.update { it.copy(externalUpdatePending = false) }
        loadData(reload = true)
    }

    /** Master chose to keep editing; the external change stays applied underneath and the next save wins. */
    fun dismissExternalUpdate() {
        _uiState.update { it.copy(externalUpdatePending = false) }
    }

    fun refreshData() {
        viewModelScope.launch {
            insulinManager.loadSettings()
            val insulins = insulinManager.insulins.map { it.deepClone() }
            val activeLabel = profileFunction.getProfile()?.iCfg?.insulinLabel
            _uiState.update {
                it.copy(
                    insulins = insulins,
                    activeInsulinLabel = activeLabel
                )
            }
        }
    }

    fun updateCurrentCardIndex(index: Int) {
        if (index == uiState.value.currentCardIndex) return
        if (hasUnsavedChanges()) {
            _uiState.update { it.copy(pendingNavigation = PendingNavigation.CardSwitch(index)) }
            return
        }
        applyCardSwitch(index)
    }

    private fun applyCardSwitch(index: Int) {
        val insulins = uiState.value.insulins
        val iCfg = insulins.getOrNull(index) ?: return
        val editorTemplate = InsulinType.fromPeak(iCfg.insulinPeakTime)
        val editorNickname = iCfg.insulinNickname.takeIf { it.isNotBlank() } ?: rh.gs(editorTemplate.label)
        val defaultNickname = rh.gs(editorTemplate.label)
        val autoNameEnabled = editorNickname == defaultNickname

        _uiState.update {
            it.copy(
                currentCardIndex = index,
                pendingNavigation = null,
                editorNickname = editorNickname,
                editorTemplate = editorTemplate,
                editorConcentration = ConcentrationType.fromDouble(iCfg.concentration),
                editorPeakMinutes = iCfg.peak,
                editorDiaHours = iCfg.dia,
                autoNameEnabled = autoNameEnabled
            )
        }
    }

    fun requestBack() {
        if (hasUnsavedChanges()) {
            _uiState.update { it.copy(pendingNavigation = PendingNavigation.Back) }
        } else {
            _uiState.update { it.copy(pendingNavigation = null) }
            viewModelScope.launch { _sideEffect.emit(SideEffect.NavigateBack) }
        }
    }

    fun saveAndProceed() {
        val pending = uiState.value.pendingNavigation
        if (saveCurrentInsulin()) {
            when (pending) {
                is PendingNavigation.CardSwitch -> applyCardSwitch(pending.targetIndex)

                is PendingNavigation.Back       -> {
                    _uiState.update { it.copy(pendingNavigation = null) }
                    viewModelScope.launch { _sideEffect.emit(SideEffect.NavigateBack) }
                }

                null                            -> Unit
            }
        }
    }

    fun discardAndProceed() {
        when (val pending = uiState.value.pendingNavigation) {
            is PendingNavigation.CardSwitch -> applyCardSwitch(pending.targetIndex)

            is PendingNavigation.Back       -> {
                _uiState.update { it.copy(pendingNavigation = null) }
                viewModelScope.launch { _sideEffect.emit(SideEffect.NavigateBack) }
            }

            null                            -> Unit
        }
    }

    fun dismissPendingNavigation() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    fun hasUnsavedChanges(): Boolean {
        val state = uiState.value
        if (state.screenMode == ScreenMode.PLAY) return false
        val stored = state.insulins.getOrNull(state.currentCardIndex)
        return stored?.let { s ->
            state.editorNickname != s.insulinNickname ||
                state.editorPeakMinutes != s.peak ||
                state.editorDiaHours != s.dia ||
                state.editorConcentration.value != s.concentration
        } ?: state.editorNickname.isNotEmpty()
    }

    // Editor field updates

    fun updateEditorNickname(nickname: String) {
        _uiState.update {
            it.copy(
                autoNameEnabled = false,
                editorNickname = nickname
            )
        }
    }

    fun updateEditorConcentration(concentration: ConcentrationType) {
        _uiState.update { it.copy(editorConcentration = concentration) }
    }

    fun updateEditorPeak(peakMinutes: Int) {
        val editorTemplate = InsulinType.fromPeak(peakMinutes.toLong() * 60_000L)
        _uiState.update {
            it.copy(
                editorTemplate = editorTemplate,
                editorPeakMinutes = peakMinutes
            )
        }
        if (uiState.value.autoNameEnabled)
            autoGenerateName()
    }

    fun updateEditorDia(diaHours: Double) {
        _uiState.update { it.copy(editorDiaHours = diaHours) }
    }

    /** Load peak from a preset template (chips UI). Only sets peak, not DIA. */
    fun loadPeakFromPreset(preset: InsulinType) {
        _uiState.update {
            it.copy(
                editorTemplate = preset,
                editorPeakMinutes = preset.iCfg.peak,
                editorNickname = rh.gs(preset.label),
                autoNameEnabled = true
            )
        }
    }

    fun autoGenerateName() {
        val newDefaultNickname = rh.gs((uiState.value.editorTemplate ?: InsulinType.fromPeak(uiState.value.editorPeakMinutes.toLong() * 60_000L)).label)
        _uiState.update { it.copy(editorNickname = newDefaultNickname) }
    }

    // CRUD operations

    fun saveCurrentInsulin(): Boolean {
        val state = uiState.value

        val nickname = state.editorNickname.trim()
        if (nickname.isEmpty()) {
            showSnackbar(rh.gs(R.string.missing_insulin_name))
            return false
        }

        // Resolve the write target in the AUTHORITATIVE live list by the identity (label) of the entry
        // the editor was bound to — never by a raw index into the UI snapshot. An external sync push the
        // master chose to keep editing over can have reordered or removed entries underneath us, so an
        // index into the stale snapshot could otherwise redirect the edit onto a different insulin or
        // drop it silently. buildFullName/uniqueness below use this same live-list index.
        val originalLabel = state.insulins.getOrNull(state.currentCardIndex)?.insulinLabel
        val targetIndex = insulinManager.insulins.indexOfFirst { it.insulinLabel == originalLabel }
        if (targetIndex < 0) {
            // The insulin we were editing no longer exists (removed by an external sync). Surface it and
            // reload rather than silently reporting success on a write that goes nowhere.
            showSnackbar(rh.gs(R.string.insulin_edit_target_gone))
            loadData(reload = true)
            return false
        }

        val fullName = insulinManager.buildFullName(
            nickname = nickname,
            peak = state.editorPeakMinutes,
            dia = state.editorDiaHours,
            concentration = state.editorConcentration.value,
            excludeIndex = targetIndex
        )

        val editedICfg = ICfg(
            insulinLabel = fullName,
            insulinEndTime = 0,
            insulinPeakTime = 0,
            concentration = state.editorConcentration.value
        )
        editedICfg.insulinNickname = nickname
        editedICfg.setDia(state.editorDiaHours)
        editedICfg.setPeak(state.editorPeakMinutes)

        // Validation
        if (editedICfg.dia < hardLimits.minDia() || editedICfg.dia > hardLimits.maxDia()) {
            showSnackbar(rh.gs(CoreUiR.string.value_out_of_hard_limits, rh.gs(CoreUiR.string.insulin_dia), editedICfg.dia))
            return false
        }
        if (editedICfg.peak < hardLimits.minPeak() || editedICfg.peak > hardLimits.maxPeak()) {
            showSnackbar(rh.gs(CoreUiR.string.value_out_of_hard_limits, rh.gs(CoreUiR.string.insulin_peak), editedICfg.peak))
            return false
        }

        // Check name uniqueness against the live list (same source as the write target).
        val existingIndex = insulinManager.insulins.indexOfFirst { it.insulinLabel == editedICfg.insulinLabel }
        if (existingIndex >= 0 && existingIndex != targetIndex) {
            showSnackbar(rh.gs(R.string.insulin_name_exists, editedICfg.insulinLabel))
            return false
        }

        // Apply to plugin
        val stored = insulinManager.insulins[targetIndex]
        stored.insulinLabel = editedICfg.insulinLabel
        stored.insulinEndTime = editedICfg.insulinEndTime
        stored.insulinPeakTime = editedICfg.insulinPeakTime
        stored.concentration = editedICfg.concentration
        stored.insulinNickname = editedICfg.insulinNickname

        // Sync UI state before store to avoid FAB lag/blocking and ensure hasUnsavedChanges() is false
        _uiState.update { it.copy(insulins = insulinManager.insulins.map { it.deepClone() }) }

        uel.log(Action.STORE_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(editedICfg.insulinLabel))
        insulinManager.storeSettings()
        lastAppliedConfig = preferences.get(StringNonKey.InsulinConfiguration) // mark as our own write
        loadData(reload = false)
        return true
    }

    fun addNewInsulin() {
        val state = uiState.value
        val source = state.insulins.getOrNull(state.currentCardIndex)
        val newICfg = source?.deepClone() ?: InsulinType.OREF_RAPID_ACTING.iCfg
        newICfg.insulinLabel = ""
        insulinManager.addNewInsulin(newICfg)

        // Sync UI state before store to ensure hasUnsavedChanges() is false
        _uiState.update { it.copy(insulins = insulinManager.insulins.map { it.deepClone() }) }

        lastAppliedConfig = preferences.get(StringNonKey.InsulinConfiguration) // mark as our own write
        loadData(targetIndex = insulinManager.currentInsulinIndex, reload = false, autoName = state.autoNameEnabled, saveAfterAutoName = true)
    }

    fun deleteCurrentInsulin(): Boolean {
        val state = uiState.value
        if (state.insulins.size <= 1) {
            showSnackbar(rh.gs(R.string.cannot_delete_last_insulin))
            return false
        }
        val currentICfg = state.insulins.getOrNull(state.currentCardIndex) ?: return false
        if (currentICfg.insulinLabel == state.activeInsulinLabel) {
            showSnackbar(rh.gs(R.string.cannot_delete_active_insulin))
            return false
        }

        insulinManager.currentInsulinIndex = state.currentCardIndex
        insulinManager.removeCurrentInsulin() // persists internally (storeSettings)

        lastAppliedConfig = preferences.get(StringNonKey.InsulinConfiguration) // mark as our own write
        // Full resync in one atomic update: insulins + coerced index + editor for the new current card.
        // (A partial insulins-only pre-update would leave the editor on the removed insulin, transiently
        //  reporting hasUnsavedChanges() == true — and out-of-bounds when the last card is deleted.)
        loadData(reload = false)
        return true
    }

    /**
     * Ask the MASTER to PREPARE the insulin activation (validate it has an active profile to re-apply, author the
     * confirmation). Client = signed round-trip; master = local. The user confirms the master's exact lines and
     * [commit] applies it. appScope: the round-trip can outlive the screen.
     */
    fun prepareActivation() {
        // Ignore re-taps while a prepare is already in flight (the round-trip can take a moment on a client).
        if (!activating.compareAndSet(false, true)) return
        appScope.launch {
            try {
                val state = uiState.value
                val iCfg = state.insulins.getOrNull(state.currentCardIndex) ?: return@launch
                val label = rh.gs(CoreUiR.string.activate_insulin)
                when (val prepared = batchExecutor.prepare(listOf(BatchAction.InsulinActivate(iCfg)), Sources.Insulin, label)) {
                    is ActionProgress.Prepared -> _sideEffect.emit(SideEffect.ShowConfirmation(prepared.id, prepared.lines))
                    // Offline block (and a master-local failure, e.g. no active profile) surface here; a client round-trip
                    // failure already showed on the app-level modal.
                    is ActionProgress.Rejected ->
                        if (prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled) showSnackbar(rh.gs(prepared.reason.failTextResId()))
                        else prepared.detail?.let { showSnackbar(it) }

                    else                       -> Unit // Unconfirmed → app-level modal
                }
            } finally {
                activating.set(false)
            }
        }
    }

    /** Confirm the master's prepared activation: apply it exactly once. On Applied: confirm + refresh (client chip follows sync-back). */
    fun commit(bolusId: Long) {
        appScope.launch {
            val result = batchExecutor.commit(bolusId, Sources.Insulin, rh.gs(CoreUiR.string.activate_insulin))
            when {
                result is ActionProgress.Applied                                                 -> {
                    showSnackbar(rh.gs(R.string.insulin_activation_applied), EventShowSnackbar.Type.Info)
                    refreshData()
                }

                result is ActionProgress.Rejected && (result.reason == FailureReason.NotReachable || result.reason == FailureReason.ControlDisabled) -> showSnackbar(rh.gs(result.reason.failTextResId()))
                result is ActionProgress.Rejected                                                -> result.detail?.let { showSnackbar(it) }
            }
        }
    }

    // Helpers

    /** Build an ICfg from current editor state */
    fun buildEditorICfg(): ICfg {
        val state = uiState.value
        val iCfg = ICfg(
            insulinLabel = state.editorNickname,
            insulinEndTime = 0,
            insulinPeakTime = 0,
            concentration = state.editorConcentration.value
        )
        iCfg.insulinNickname = state.editorNickname
        iCfg.setDia(state.editorDiaHours)
        iCfg.setPeak(state.editorPeakMinutes)
        return iCfg
    }

    private fun showSnackbar(message: String, type: EventShowSnackbar.Type = EventShowSnackbar.Type.Error) {
        rxBus.send(EventShowSnackbar(message, type))
    }

    /** Preset list for "Load peak from" chips — excludes FreePeak (not a real preset) */
    fun presetList(): List<InsulinType> = insulinManager.insulinTemplateList().filter { it != InsulinType.OREF_FREE_PEAK }
    fun concentrationList(): List<ConcentrationType> = insulinManager.concentrationList()
    val concentrationEnabled: Boolean
        get() = preferences.get(BooleanKey.GeneralInsulinConcentration)

    fun diaRange(): ClosedFloatingPointRange<Double> = hardLimits.minDia()..hardLimits.maxDia()
    fun peakRange(): ClosedFloatingPointRange<Double> = hardLimits.minPeak().toDouble()..hardLimits.maxPeak().toDouble()
}
