package app.aaps.plugins.source.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GV
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
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.SnackbarMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel for BgSourceScreen managing blood glucose readings state and business logic.
 */
@HiltViewModel
@Stable
class BgSourceViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    internal val rh: ResourceHelper,
    internal val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<BgSourceUiState>
        field = MutableStateFlow(BgSourceUiState())

    /** Default time range for BG source history */
    private val defaultHistoryHours = 36L

    init {
        loadData()
        observeBgChanges()
    }

    /**
     * Load blood glucose readings
     */
    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.glucoseValues.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.hours(currentState.historyHours).msecs()

                val data = persistenceLayer.getBgReadingsDataFromTime(now - millsToThePast, false)

                // Pre-compute duplicate IDs (items within 20s of previous) to avoid O(n²) in UI
                val duplicateIds = buildSet {
                    for (i in 1 until data.size) {
                        if ((data[i - 1].timestamp - data[i].timestamp) < T.secs(20).msecs()) {
                            add(data[i].id)
                        }
                    }
                }

                uiState.update {
                    it.copy(
                        glucoseValues = data,
                        duplicateIds = duplicateIds,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load BG data", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading BG data")
                    )
                }
            }
        }
    }

    /**
     * Load more historical data (for infinite scroll)
     */
    fun loadMoreData() {
        uiState.update { it.copy(historyHours = it.historyHours + 24L) }
        loadData()
    }

    /**
     * Subscribe to BG change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeBgChanges() {
        persistenceLayer.observeChanges<GV>()
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
     * Format glucose value to string in user's preferred units
     */
    fun formatGlucoseValue(value: Double): String = profileUtil.fromMgdlToStringInUnits(value)

    /**
     * Enter selection mode with initial item selected
     */
    fun enterSelectionMode(item: GV) {
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
    fun toggleSelection(item: GV) {
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
            val gv = selected.first()
            "${dateUtil.dateAndTimeString(gv.timestamp)}\n${profileUtil.fromMgdlToUnits(gv.value)}"
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
                selected.forEach { gv ->
                    persistenceLayer.invalidateGlucoseValue(
                        gv.id,
                        action = Action.BG_REMOVED,
                        source = Sources.BgFragment,
                        note = null,
                        listValues = listOf(ValueWithUnit.Timestamp(gv.timestamp))
                    )
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete BG readings", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting BG readings")) }
            }
        }
    }
}

/**
 * UI state for BgSourceScreen
 */
@Immutable
data class BgSourceUiState(
    val glucoseValues: List<GV> = emptyList(),
    val duplicateIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<GV> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null,
    val historyHours: Long = 36L
)
