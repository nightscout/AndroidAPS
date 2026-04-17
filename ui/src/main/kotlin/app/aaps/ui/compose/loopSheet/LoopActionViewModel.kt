package app.aaps.ui.compose.loopSheet

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
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
data class LoopActionUiState(
    val actionAvailable: Boolean = false,
    val request: String = "",
    val reason: String = ""
)

@HiltViewModel
@Stable
class LoopActionViewModel @Inject constructor(
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper,
    private val rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoopActionUiState())
    val uiState: StateFlow<LoopActionUiState> = _uiState.asStateFlow()

    init {
        rxBus.toFlow(EventLoopUpdateGui::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventNewOpenLoopNotification::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventRefreshOverview::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventAcceptOpenLoopChange::class.java)
            .onEach { refreshState() }.launchIn(viewModelScope)
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val lastRun = loop.lastRun

        val resultAvailable = lastRun != null &&
            (lastRun.lastOpenModeAccept == 0L || lastRun.lastOpenModeAccept < lastRun.lastAPSRun) &&
            lastRun.constraintsProcessed?.isChangeRequested == true

        val available = resultAvailable &&
            pump.isInitialized() &&
            profile != null &&
            loop.runningMode == RM.Mode.OPEN_LOOP &&
            (loop as PluginBase).isEnabled()

        _uiState.update {
            if (available) {
                LoopActionUiState(
                    actionAvailable = true,
                    request = lastRun?.constraintsProcessed?.resultAsString().orEmpty(),
                    reason = rh.gs(app.aaps.ui.R.string.loop_accept_set_basal_question)
                )
            } else {
                LoopActionUiState(actionAvailable = false)
            }
        }
    }
}
