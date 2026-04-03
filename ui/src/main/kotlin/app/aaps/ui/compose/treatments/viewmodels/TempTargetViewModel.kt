package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.friendlyDescription
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
 * ViewModel for TempTargetScreen managing temporary target state and business logic.
 */
@Stable
class TempTargetViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<TempTargetUiState>
        field = MutableStateFlow(TempTargetUiState())

    init {
        loadData()
        observeTempTargetChanges()
    }

    /**
     * Load temporary targets
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.tempTargets.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()

                val tempTargets = if (currentState.showInvalidated) {
                    persistenceLayer.getTemporaryTargetDataIncludingInvalidFromTime(now - millsToThePast, false)
                } else {
                    persistenceLayer.getTemporaryTargetDataFromTime(now - millsToThePast, false)
                }

                uiState.update {
                    it.copy(
                        tempTargets = tempTargets,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load temp targets", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading temp targets")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to temp target change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeTempTargetChanges() {
        persistenceLayer
            .observeChanges<TT>()
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
    fun enterSelectionMode(item: TT) {
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
    fun toggleSelection(item: TT) {
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
     * Get currently active temporary target
     */
    fun getActiveTarget(): TT? {
        return runBlocking { persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) }
    }

    /**
     * Prepare delete confirmation message
     */
    fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val tt = selected.first()
            "${rh.gs(R.string.temporary_target)}: ${tt.friendlyDescription(profileUtil.units, rh, profileUtil)}\n${dateUtil.dateAndTimeString(tt.timestamp)}"
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
                selected.forEach { tt ->
                    persistenceLayer.invalidateTemporaryTarget(
                        id = tt.id,
                        action = Action.TT_REMOVED,
                        source = Sources.Treatments,
                        note = null,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(tt.timestamp),
                            ValueWithUnit.TETTReason(tt.reason),
                            ValueWithUnit.Mgdl(tt.lowTarget),
                            ValueWithUnit.Mgdl(tt.highTarget).takeIf { tt.lowTarget != tt.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tt.duration).toInt())
                        )
                    )
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete temp targets", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting temp targets")) }
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
 * UI state for TempTargetScreen
 */
@Immutable
data class TempTargetUiState(
    val tempTargets: List<TT> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<TT> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
