package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.MenuItemData
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.TREATMENT_HISTORY_DAYS
import kotlinx.coroutines.Dispatchers
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
 * ViewModel for CareportalScreen managing therapy event state and business logic.
 */
@Stable
class CareportalViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    val rh: ResourceHelper,
    private val translator: Translator,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<CareportalUiState>
        field = MutableStateFlow(CareportalUiState())

    init {
        loadData()
        observeTherapyEventChanges()
    }

    /**
     * Load therapy events
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.therapyEvents.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()

                val therapyEvents = if (currentState.showInvalidated) {
                    persistenceLayer.getTherapyEventDataIncludingInvalidFromTime(now - millsToThePast, false)
                } else {
                    persistenceLayer.getTherapyEventDataFromTime(now - millsToThePast, false)
                }

                uiState.update {
                    it.copy(
                        therapyEvents = therapyEvents,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load therapy events", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading therapy events")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to therapy event change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeTherapyEventChanges() {
        persistenceLayer
            .observeChanges<TE>()
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
     * Toggle show/hide invalidated items
     */
    fun toggleInvalidated() {
        uiState.update { it.copy(showInvalidated = !it.showInvalidated) }
        loadData()
    }

    /**
     * Enter selection mode with initial item selected
     */
    fun enterSelectionMode(item: TE) {
        uiState.update {
            it.copy(
                isRemovingMode = true,
                selectedItems = setOf(item)
            )
        }
    }

    /**
     * Exit selection mode and clear selection
     */
    fun exitSelectionMode() {
        uiState.update {
            it.copy(
                isRemovingMode = false,
                selectedItems = emptySet()
            )
        }
    }

    /**
     * Toggle selection of an item
     */
    fun toggleSelection(item: TE) {
        uiState.update { state ->
            val newSelection = if (item in state.selectedItems) {
                state.selectedItems - item
            } else {
                state.selectedItems + item
            }
            state.copy(selectedItems = newSelection)
        }
    }

    /**
     * Prepare delete confirmation message
     */
    fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val te = selected.first()
            "${rh.gs(R.string.event_type)}: ${translator.translate(te.type)}\n" +
                "${rh.gs(R.string.notes_label)}: ${te.note ?: ""}\n" +
                "${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(te.timestamp)}"
        } else {
            rh.gs(R.string.confirm_remove_multiple_items, selected.size)
        }
    }

    /**
     * Delete selected items
     */
    fun deleteSelected() {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                selected.forEach { te ->
                    persistenceLayer.invalidateTherapyEvent(
                        id = te.id,
                        action = Action.CAREPORTAL_REMOVED,
                        source = Sources.Treatments,
                        note = te.note,
                        listValues = listOf(
                            ValueWithUnit.Timestamp(te.timestamp),
                            ValueWithUnit.TEType(te.type)
                        )
                    )
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete therapy events", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting therapy events")) }
            }
        }
    }

    /**
     * Get toolbar configuration for current state
     */
    fun getToolbarConfig(onNavigateBack: () -> Unit, onDeleteClick: () -> Unit, menuItems: List<MenuItemData>): ToolbarConfig {
        val state = uiState.value
        return SelectableListToolbar(
            isRemovingMode = state.isRemovingMode,
            selectedCount = state.selectedItems.size,
            onExitRemovingMode = { exitSelectionMode() },
            onNavigateBack = onNavigateBack,
            onDelete = {
                if (state.selectedItems.isNotEmpty()) {
                    onDeleteClick()
                }
            },
            rh = rh,
            showInvalidated = state.showInvalidated,
            onToggleInvalidated = { toggleInvalidated() },
            menuItems = menuItems
        )
    }
}

/**
 * UI state for CareportalScreen
 */
@Immutable
data class CareportalUiState(
    val therapyEvents: List<TE> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<TE> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
