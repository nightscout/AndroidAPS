package app.aaps.ui.compose.runningMode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.SnackbarMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val translator: Translator,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<RunningModeManagementUiState>
        field = MutableStateFlow(RunningModeManagementUiState())

    init {
        loadState()
        observeRunningModeChanges()
    }

    /**
     * Load current running mode state and allowed transitions
     */
    fun loadState() {
        viewModelScope.launch {
            try {
                val runningModeRecord = loop.runningModeRecord
                val currentMode = runningModeRecord.mode
                val allowedModes = loop.allowedNextModes()
                val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription

                uiState.update {
                    it.copy(
                        currentMode = currentMode,
                        currentModeText = translator.translate(currentMode),
                        reasons = runningModeRecord.reasons,
                        allowedNextModes = allowedModes,
                        isApsMode = config.APS,
                        tempDurationStep15mAllowed = pumpDescription.tempDurationStep15mAllowed,
                        tempDurationStep30mAllowed = pumpDescription.tempDurationStep30mAllowed,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load running mode state", e)
                uiState.update { it.copy(isLoading = false, snackbarMessage = SnackbarMessage.Error(e.message ?: "Failed to load running mode state")) }
            }
        }
    }

    /**
     * Subscribe to running mode changes to auto-refresh UI
     */
    @OptIn(FlowPreview::class)
    private fun observeRunningModeChanges() {
        persistenceLayer
            .observeChanges<RM>()
            .debounce(500L)
            .onEach { loadState() }
            .launchIn(viewModelScope)
    }

    /**
     * Execute a running mode action
     *
     * @param targetMode The target running mode
     * @param action The action being performed
     * @param durationMinutes Duration in minutes (for temporary modes)
     * @return true if action was successful
     */
    fun executeAction(
        targetMode: RM.Mode,
        action: Action,
        durationMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val profile = profileFunction.getProfile() ?: return@launch

            val success = loop.handleRunningModeChange(
                newRM = targetMode,
                action = action,
                source = Sources.LoopDialog,
                profile = profile,
                durationInMinutes = durationMinutes
            )

            // Track objectives usage for specific actions
            if (success) {
                when (action) {
                    Action.RESUME, Action.RECONNECT -> {
                        preferences.put(BooleanNonKey.ObjectivesReconnectUsed, true)
                    }

                    Action.DISCONNECT               -> {
                        if (durationMinutes >= 60) {
                            preferences.put(BooleanNonKey.ObjectivesDisconnectUsed, true)
                        }
                    }

                    else                            -> { /* no tracking needed */
                    }
                }
            }
        }
    }

    /**
     * Clear error state
     */
    fun clearSnackbar() {
        uiState.update { it.copy(snackbarMessage = null) }
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
    val isApsMode: Boolean = false,
    val tempDurationStep15mAllowed: Boolean = false,
    val tempDurationStep30mAllowed: Boolean = false,
    val isLoading: Boolean = true,
    val snackbarMessage: SnackbarMessage? = null
)
