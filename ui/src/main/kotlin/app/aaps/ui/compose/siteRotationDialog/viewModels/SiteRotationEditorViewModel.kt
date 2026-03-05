package app.aaps.ui.compose.siteRotationDialog.viewModels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class SiteRotationEditorViewModel @Inject constructor(
    var rh: ResourceHelper,
    var uel: UserEntryLogger,
    var rxBus: RxBus,
    var dateUtil: DateUtil,
    var persistenceLayer: PersistenceLayer,
    var translator: Translator
) : ViewModel() {

    // Placeholder for future UI state
    data class UiState(
        val isLoading: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadEntryByTimestamp(timestamp: Long) {
        viewModelScope.launch {
            // Simulation d'un chargement (à remplacer par un vrai appel DB)

        }
    }
    // The TE will be passed directly to the screen
    // Future functions will be added here
}