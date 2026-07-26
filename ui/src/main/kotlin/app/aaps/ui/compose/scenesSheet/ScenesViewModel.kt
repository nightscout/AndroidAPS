package app.aaps.ui.compose.scenesSheet

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationIconData
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

@Immutable
data class AutomationActionItem(
    val eventId: String,
    val title: String,
    val firstActionIcon: AutomationIconData?,
    val actionsDescription: List<String> = emptyList(),
    val triggerIcons: List<AutomationIconData> = emptyList(),
    val actionIcons: List<AutomationIconData> = emptyList(),
    /** Localized reason why this item is currently not activatable, or null if it is. */
    val activationReason: String? = null
)

@Immutable
data class SceneSheetItem(
    val id: String,
    val name: String,
    val actionCount: Int,
    val iconKey: String,
    /** Localized reason why this scene is currently not activatable, or null if it is. */
    val activationReason: String? = null
)

@Immutable
data class ScenesUiState(
    val items: List<AutomationActionItem> = emptyList(),
    val sceneItems: List<SceneSheetItem> = emptyList()
)

@HiltViewModel
@Stable
class ScenesViewModel @Inject constructor(
    private val automation: Automation,
    private val activePlugin: ActivePlugin,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val rxBus: RxBus,
    private val sceneRepository: SceneStore,
    private val sceneActions: SceneActions,
    private val rh: ResourceHelper,
    private val nsClient: NsClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScenesUiState())
    val uiState: StateFlow<ScenesUiState> = _uiState.asStateFlow()

    /** AAPSCLIENT-only WS-reachability signal — overlays "Master disconnected" on every gate. */
    private val masterReachable: StateFlow<Boolean> = nsClient.masterReachable

    init {
        setupEventListeners()
        refreshState()
    }

    private fun setupEventListeners() {
        // RxBus flows are hot and don't replay, so initial subscription doesn't refresh —
        // init { refreshState() } below covers the cold start.
        rxBus.toFlow(EventRefreshOverview::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        // StateFlow — drop(1) since init{} already reads current automation events; only react to changes.
        automation.events.drop(1)
            .onEach { refreshState() }.launchIn(viewModelScope)
        // Pump init / loop mode / profile load can flip the automation gate.
        // Without these, transient "no profile / pump disconnected" windows
        // would wipe automation items and never restore them until another
        // event (e.g. editing a scene) re-fired refreshState.
        rxBus.toFlow(EventPumpStatusChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventLoopUpdateGui::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventInitializationChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        // StateFlow — drop(1) since init{} already reads current scenes; only react to changes.
        sceneRepository.scenesFlow
            .drop(1).onEach { refreshState() }.launchIn(viewModelScope)
        // Reachability flips need to re-render — same path as a scene/automation refresh.
        masterReachable
            .drop(1).onEach { refreshState() }.launchIn(viewModelScope)
    }

    fun refreshState() {
        viewModelScope.launch {
            // On AAPSCLIENT, master-disconnected overrides every per-scene / per-automation
            // gate — the action can't reach master regardless of what the local validator says.
            val masterOfflineReason: String? =
                if (!masterReachable.value)
                    rh.gs(if (!nsClient.masterControlAllowed.value) CoreUiR.string.scene_lock_reason_control_disabled else CoreUiR.string.scene_lock_reason_master_offline)
                else null

            // Scenes are *definitions* — show them regardless of pump/loop/profile state.
            // Per-scene activation gating is computed via the shared validator and
            // surfaced as activationReason; the UI renders disabled scenes dimmed
            // with the reason as a subtitle. SceneExecutor.activate() also gates
            // defensively, so non-UI callers can't bypass.
            val scenes = sceneRepository.getScenes().filter { it.isEnabled }.map { scene ->
                SceneSheetItem(
                    id = scene.id,
                    name = scene.name,
                    actionCount = scene.actions.size,
                    iconKey = scene.icon,
                    activationReason = masterOfflineReason ?: sceneActions.validateActivation(scene)
                )
            }

            // Automation user actions DO depend on a runnable runtime. Instead of
            // hiding items under bad conditions, show them disabled with a reason
            // — same UX as scenes. Watch-only mode still hides them entirely since
            // they belong on the watch face in that configuration.
            val watchOnly = config.isEnabled(ExternalOptions.SHOW_USER_ACTIONS_ON_WATCH_ONLY)
            val automationReason: String? = when {
                watchOnly                                -> null  // hidden, not disabled
                masterOfflineReason != null              -> masterOfflineReason
                loop.runningMode().pausesLoopExecution() -> rh.gs(CoreUiR.string.pump_disconnected)
                !activePlugin.activePump.isInitialized() ||
                    profileFunction.getProfile() == null -> rh.gs(CoreUiR.string.pump_not_initialized_profile_not_set)

                else                                     -> null
            }

            // Read directly from the flow's current snapshot — same source we already collect for
            // refresh triggers, so the displayed list matches the value that caused the refresh.
            // (Previously called automation.userEvents() which re-snapshots independently and was
            // also pre-filtering isEnabled — making the inline filter partly redundant.)
            // Automation executes on master only — a client never lists runnable user actions.
            val items = if (watchOnly || !automation.executionEnabled) emptyList()
            else automation.events.value.filter { it.userAction && it.isEnabled && it.canRun() }.map { event ->
                AutomationActionItem(
                    eventId = event.id,
                    title = event.title,
                    firstActionIcon = event.firstActionIcon(),
                    actionsDescription = event.actionsDescription(),
                    triggerIcons = event.triggerIcons().toList(),
                    actionIcons = event.actionIcons().toList(),
                    activationReason = automationReason
                )
            }

            _uiState.update { it.copy(items = items, sceneItems = scenes) }
        }
    }

}
