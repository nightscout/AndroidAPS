package app.aaps.plugins.sync.nsShared.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.R
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class NSClientUiState(
    val url: String = "",
    val status: String = "",
    val queue: String = "",
    val paused: Boolean = false,
    val logList: List<NSClientLog> = emptyList()
)

@HiltViewModel
@Stable
class NSClientViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val nsClientRepository: NSClientRepository,
    private val preferences: Preferences
) : ViewModel() {

    private val nsClientPlugin get() = activePlugin.activeNsClient

    // UI state
    val uiState: StateFlow<NSClientUiState>
        field = MutableStateFlow(NSClientUiState())

    init {
        viewModelScope.launch {
            nsClientRepository.queueSize.collect { size ->
                val queueText = if (size >= 0) size.toString() else rh.gs(R.string.value_unavailable_short)
                uiState.update { it.copy(queue = queueText) }
            }
        }
        viewModelScope.launch {
            nsClientRepository.statusUpdate.collect { status ->
                uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            nsClientRepository.logList.collect { logList ->
                uiState.update { it.copy(logList = logList) }
            }
        }
        viewModelScope.launch {
            nsClientRepository.urlUpdate.collect { url ->
                uiState.update { it.copy(url = url) }
            }
        }
    }

    fun loadInitialData() {
        uiState.update {
            it.copy(paused = preferences.get(NsclientBooleanKey.NsPaused))
        }
    }

    fun updatePaused(paused: Boolean) {
        uiState.update { it.copy(paused = paused) }
    }
}
