package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.RM
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
import app.aaps.core.ui.R
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
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for RunningModeScreen managing running mode state and business logic.
 */
@Stable
class RunningModeViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<RunningModeUiState>
        field = MutableStateFlow(RunningModeUiState())

    init {
        loadData()
        observeRunningModeChanges()
    }

    /**
     * Load running modes
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.runningModes.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()

                val runningModes = if (currentState.showInvalidated) {
                    persistenceLayer.getRunningModesIncludingInvalidFromTime(now - millsToThePast, false)
                } else {
                    persistenceLayer.getRunningModesFromTime(now - millsToThePast, false)
                }

                uiState.update {
                    it.copy(
                        runningModes = runningModes,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load running modes", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading running modes")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to running mode change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeRunningModeChanges() {
        persistenceLayer
            .observeChanges<RM>()
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
    fun enterSelectionMode(item: RM) {
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
    fun toggleSelection(item: RM) {
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
     * Get currently active running mode
     */
    fun getActiveMode(): RM {
        return runBlocking { persistenceLayer.getRunningModeActiveAt(dateUtil.now()) }
    }

    /**
     * Prepare delete confirmation message
     */
    fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val rm = selected.first()
            "${rh.gs(R.string.running_mode)}: ${rm.mode.name}\n${dateUtil.dateAndTimeString(rm.timestamp)}"
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
                selected.forEach { rm ->
                    persistenceLayer.invalidateRunningMode(
                        id = rm.id,
                        action = Action.LOOP_REMOVED,
                        source = Sources.Treatments,
                        note = null,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(rm.timestamp),
                            ValueWithUnit.RMMode(rm.mode),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(rm.duration).toInt())
                        )
                    )
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting running modes")) }
            }
        }
    }

    /**
     * Get toolbar configuration for current state
     */
    fun getToolbarConfig(onNavigateBack: () -> Unit, onDeleteClick: () -> Unit): ToolbarConfig {
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
            onToggleInvalidated = { toggleInvalidated() }
        )
    }
}

/**
 * UI state for RunningModeScreen
 */
@Immutable
data class RunningModeUiState(
    val runningModes: List<RM> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<RM> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
