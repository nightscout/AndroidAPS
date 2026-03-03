package app.aaps.plugins.sync.xdrip.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class XdripUiState(
    val queue: String = "",
    val logList: List<XdripLog> = emptyList()
)

@HiltViewModel
@Stable
class XdripViewModel @Inject constructor(
    private val rh: ResourceHelper,
    private val xdripMvvmRepository: XdripMvvmRepository,
    private val dataSyncSelector: DataSyncSelectorXdrip
) : ViewModel() {

    // UI state
    val uiState: StateFlow<XdripUiState>
        field = MutableStateFlow(XdripUiState())

    init {
        viewModelScope.launch {
            xdripMvvmRepository.queueSize.collect { size ->
                val queueText = if (size >= 0) size.toString() else rh.gs(R.string.value_unavailable_short)
                uiState.update { it.copy(queue = queueText) }
            }
        }
        viewModelScope.launch {
            xdripMvvmRepository.logList.collect { logList ->
                uiState.update { it.copy(logList = logList) }
            }
        }
    }

    fun loadInitialData() {
        val size = dataSyncSelector.queueSize()
        xdripMvvmRepository.updateQueueSize(size)
    }
}
