package app.aaps.ui.compose.automationSheet

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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutomationDataChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AutomationActionItem(
    val eventId: String,
    val title: String,
    val firstActionIcon: AutomationIconData?,
    val actionsDescription: List<String> = emptyList(),
    val triggerIcons: List<AutomationIconData> = emptyList(),
    val actionIcons: List<AutomationIconData> = emptyList()
)

@Immutable
data class AutomationUiState(
    val items: List<AutomationActionItem> = emptyList()
)

@HiltViewModel
@Stable
class AutomationViewModel @Inject constructor(
    private val automation: Automation,
    private val activePlugin: ActivePlugin,
    private val loop: Loop,
    private val profileFunction: ProfileFunction,
    private val config: Config,
    private val rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        setupEventListeners()
        refreshState()
    }

    private fun setupEventListeners() {
        rxBus.toFlow(EventRefreshOverview::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventAutomationDataChanged::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
    }

    fun refreshState() {
        viewModelScope.launch {
            val pump = activePlugin.activePump
            val profile = profileFunction.getProfile()

            val isSuspended = loop.runningMode.isSuspended()
            if (isSuspended || !pump.isInitialized() || profile == null || config.isEnabled(ExternalOptions.SHOW_USER_ACTIONS_ON_WATCH_ONLY)) {
                _uiState.update { it.copy(items = emptyList()) }
                return@launch
            }

            val events = automation.userEvents().filter { it.isEnabled && it.canRun() }

            val items = events.map { event ->
                AutomationActionItem(
                    eventId = event.id,
                    title = event.title,
                    firstActionIcon = event.firstActionIcon(),
                    actionsDescription = event.actionsDescription(),
                    triggerIcons = event.triggerIcons().toList(),
                    actionIcons = event.actionIcons().toList()
                )
            }
            _uiState.update { it.copy(items = items) }
        }
    }

}
