package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class TidepoolUiState(
    val connectionStatus: String = "",
    val logList: List<TidepoolLog> = emptyList()
)

@HiltViewModel
@Stable
class TidepoolViewModel @Inject constructor(
    private val tidepoolRepository: TidepoolRepository,
    private val authFlowOut: AuthFlowOut
) : ViewModel() {

    private val _uiState = MutableStateFlow(TidepoolUiState())
    val uiState: StateFlow<TidepoolUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tidepoolRepository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status.name) }
            }
        }
        viewModelScope.launch {
            tidepoolRepository.logList.collect { logList ->
                _uiState.update { it.copy(logList = logList) }
            }
        }
    }

    fun loadInitialData() {
        tidepoolRepository.updateConnectionStatus(authFlowOut.connectionStatus)
    }
}
