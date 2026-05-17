package app.aaps.plugins.sync.openhumans.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.plugins.sync.openhumans.delegates.OHStateDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
internal data class OHUiState(
    val isLoggedIn: Boolean = false,
    val projectMemberId: String? = null
)

@HiltViewModel
@Stable
internal class OHViewModel @Inject constructor(
    private val stateDelegate: OHStateDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(OHUiState())
    val uiState: StateFlow<OHUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stateDelegate.stateFlow.collect { state ->
                _uiState.update {
                    OHUiState(
                        isLoggedIn = state != null,
                        projectMemberId = state?.projectMemberId
                    )
                }
            }
        }
    }
}
