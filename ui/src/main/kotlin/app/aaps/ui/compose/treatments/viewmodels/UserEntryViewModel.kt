package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.UE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.MenuItemData
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.USER_ENTRY_FILTERED_DAYS
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.USER_ENTRY_UNFILTERED_DAYS
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
 * ViewModel for UserEntryScreen managing user entry log state and business logic.
 */
@Stable
class UserEntryViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<UserEntryUiState>
        field = MutableStateFlow(UserEntryUiState())

    private val millsToThePastFiltered = T.days(USER_ENTRY_FILTERED_DAYS).msecs()
    private val millsToThePastUnFiltered = T.days(USER_ENTRY_UNFILTERED_DAYS).msecs()

    init {
        loadData()
        observeUserEntryChanges()
    }

    /**
     * Load user entries
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.userEntries.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val userEntries = if (currentState.showLoop) {
                    persistenceLayer.getUserEntryDataFromTime(now - millsToThePastUnFiltered)
                } else {
                    persistenceLayer.getUserEntryFilteredDataFromTime(now - millsToThePastFiltered)
                }

                uiState.update {
                    it.copy(
                        userEntries = userEntries,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load user entries", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading user entries")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to user entry change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeUserEntryChanges() {
        persistenceLayer
            .observeChanges<UE>()
            .debounce(1000L) // 1 second debounce
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    /**
     * Clear error state
     */
    fun clearSnackbar() {
        uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Toggle show/hide loop records
     */
    fun toggleLoop() {
        uiState.update { it.copy(showLoop = !it.showLoop) }
        loadData()
    }

    /**
     * Get toolbar configuration for current state
     */
    fun getToolbarConfig(onNavigateBack: () -> Unit, menuItems: List<MenuItemData>): ToolbarConfig {
        val state = uiState.value
        return SelectableListToolbar(
            isRemovingMode = false,
            selectedCount = 0,
            onExitRemovingMode = { },
            onNavigateBack = onNavigateBack,
            onDelete = { },
            rh = rh,
            showLoop = state.showLoop,
            onToggleLoop = { toggleLoop() },
            menuItems = menuItems
        )
    }
}

/**
 * UI state for UserEntryScreen
 */
@Immutable
data class UserEntryUiState(
    val userEntries: List<UE> = emptyList(),
    val isLoading: Boolean = true,
    val showLoop: Boolean = false,
    val snackbarMessage: SnackbarMessage? = null
)
