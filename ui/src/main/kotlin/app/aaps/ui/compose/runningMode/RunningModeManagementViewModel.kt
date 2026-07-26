package app.aaps.ui.compose.runningMode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.ui.compose.overview.chips.toIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for RunningModeScreen (replacement for LoopDialog).
 * Handles loop mode changes, suspend/resume, and pump disconnect/reconnect.
 */
@HiltViewModel
@Stable
class RunningModeManagementViewModel @Inject constructor(
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val translator: Translator,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val batchExecutor: BatchExecutor,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningModeManagementUiState())
    val uiState: StateFlow<RunningModeManagementUiState> = _uiState.asStateFlow()

    init {
        loadState()
        observeStateChanges()
    }

    /**
     * Load current running mode state and allowed transitions
     */
    fun loadState() {
        viewModelScope.launch {
            try {
                val runningModeRecord = loop.runningModeRecord()
                val currentMode = runningModeRecord.mode
                val allowedModes = loop.allowedNextModes()
                val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription
                // Whether a profile is actually set. [Loop.allowedNextModes] returns an empty list both when no
                // profile is set AND when the pump force-suspends (SUSPENDED_BY_PUMP); only the former should
                // surface the "no profile set" card, so check the real condition instead of the empty list.
                val profileSet = profileFunction.isProfileValid("RunningModeScreen")

                _uiState.update {
                    it.copy(
                        currentMode = currentMode,
                        currentModeText = translator.translate(currentMode),
                        reasons = runningModeRecord.reasons,
                        allowedNextModes = allowedModes,
                        profileSet = profileSet,
                        tempDurationStep15mAllowed = pumpDescription.tempDurationStep15mAllowed,
                        tempDurationStep30mAllowed = pumpDescription.tempDurationStep30mAllowed,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load running mode state", e)
                _uiState.update { it.copy(isLoading = false) }
                rxBus.send(EventShowSnackbar(e.message ?: "Failed to load running mode state", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Subscribe to changes that affect the screen state and auto-refresh.
     * - Running mode (RM) changes: the current mode / reasons.
     * - Profile (EffectiveProfileSwitch / EPS) changes: [Loop.allowedNextModes] requires an active
     *   profile, so without this the screen can stay stuck on "no profile set" after a profile
     *   becomes active (the overview observes EPS, this screen previously did not).
     */
    @OptIn(FlowPreview::class)
    private fun observeStateChanges() {
        persistenceLayer
            .observeChanges<RM>()
            .compensateForClockSkew(config, dateUtil)
            .debounce(500L)
            .onEach { loadState() }
            .launchIn(viewModelScope)

        persistenceLayer
            .observeChanges<EPS>()
            .debounce(500L)
            .onEach { loadState() }
            .launchIn(viewModelScope)
    }

    /**
     * Execute a running mode change through the role-transparent [BatchExecutor]: PREPARE (the master caps/validates
     * the transition and authors the confirmation lines), show those lines as the single confirm dialog, then COMMIT
     * on OK (the master applies via `Loop.handleRunningModeChange`). A client relays the change to the master; the
     * master applies locally. [action] is kept only to credit the objectives milestone after a confirmed apply (the
     * executor re-derives the UEL action from the mode).
     *
     * @param targetMode The target running mode
     * @param action The action being performed (objectives tracking only)
     * @param durationMinutes Duration in minutes (for the temporary modes)
     */
    fun executeAction(
        targetMode: RM.Mode,
        action: Action,
        durationMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val label = rh.gs(R.string.running_mode)
            when (val prepared = batchExecutor.prepare(listOf(BatchAction.RunningMode(targetMode, durationMinutes)), Sources.LoopDialog, label)) {
                is ActionProgress.Prepared -> rxBus.send(
                    EventShowDialog.OkCancel(
                        title = label, message = "", confirmationLines = prepared.lines, icon = targetMode.toIcon(),
                        onOk = {
                            appScope.launch {
                                if (batchExecutor.commit(prepared.id, Sources.LoopDialog, label) is ActionProgress.Applied)
                                    trackObjectives(action, durationMinutes)
                            }
                        }
                    )
                )

                // Master-local validation failure, or a client offline; a client round-trip failure already showed on the app modal.
                is ActionProgress.Rejected -> {
                    if (!config.AAPSCLIENT || prepared.reason == FailureReason.NotReachable || prepared.reason == FailureReason.ControlDisabled)
                        rxBus.send(EventShowSnackbar(prepared.detail ?: rh.gs(prepared.reason.failTextResId()), EventShowSnackbar.Type.Error))
                }

                else                       -> Unit // Unconfirmed → handled by the app-level pending modal
            }
        }
    }

    /** Credit the disconnect/reconnect objectives milestones once a running-mode change is confirmed + applied. */
    private fun trackObjectives(action: Action, durationMinutes: Int) {
        when (action) {
            Action.RESUME, Action.RECONNECT -> preferences.put(BooleanNonKey.ObjectivesReconnectUsed, true)
            Action.DISCONNECT               -> if (durationMinutes >= 60) preferences.put(BooleanNonKey.ObjectivesDisconnectUsed, true)
            else                            -> Unit
        }
    }

}

/**
 * UI state for RunningModeScreen
 */
@Immutable
data class RunningModeManagementUiState(
    val currentMode: RM.Mode = RM.Mode.DISABLED_LOOP,
    val currentModeText: String = "",
    val reasons: String? = null,
    val allowedNextModes: List<RM.Mode> = emptyList(),
    val profileSet: Boolean = true,
    val tempDurationStep15mAllowed: Boolean = false,
    val tempDurationStep30mAllowed: Boolean = false,
    val isLoading: Boolean = true
)
