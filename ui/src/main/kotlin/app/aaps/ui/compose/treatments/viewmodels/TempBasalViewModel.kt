package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.core.objects.extensions.toTemporaryBasal
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for TempBasalScreen managing temporary basal state and business logic.
 */
@Stable
class TempBasalViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<TempBasalUiState>
        field = MutableStateFlow(TempBasalUiState())

    init {
        loadData()
        observeTempBasalChanges()
    }

    /**
     * Load temp basals and potentially extended boluses (if pump is faking temps)
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.tempBasals.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()
                val isFakingTempsByExtendedBoluses = activePlugin.activePump.isFakingTempsByExtendedBoluses

                val tempBasalsList = if (currentState.showInvalidated) {
                    persistenceLayer.getTemporaryBasalsStartingFromTimeIncludingInvalid(now - millsToThePast, false)
                } else {
                    persistenceLayer.getTemporaryBasalsStartingFromTime(now - millsToThePast, false)
                }

                val tempBasals = if (isFakingTempsByExtendedBoluses) {
                    val extendedBolusList = if (currentState.showInvalidated) {
                        persistenceLayer.getExtendedBolusStartingFromTimeIncludingInvalid(now - millsToThePast, false)
                    } else {
                        persistenceLayer.getExtendedBolusesStartingFromTime(now - millsToThePast, false)
                    }

                    val convertedExtendedBoluses = extendedBolusList.mapNotNull { eb ->
                        profileFunction.getProfile(eb.timestamp)?.let { profile ->
                            eb.toTemporaryBasal(profile)
                        }
                    }

                    (tempBasalsList + convertedExtendedBoluses).sortedByDescending { it.timestamp }
                } else {
                    tempBasalsList.sortedByDescending { it.timestamp }
                }

                uiState.update {
                    it.copy(
                        tempBasals = tempBasals,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load temp basals", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading temp basals")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to temp basal change events using Flow
     */
    @OptIn(FlowPreview::class)
    private fun observeTempBasalChanges() {
        persistenceLayer
            .observeChanges<TB>()
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
    fun enterSelectionMode(item: TB) {
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
    fun toggleSelection(item: TB) {
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
    suspend fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val tempBasal = selected.first()
            val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
            val profile = profileFunction.getProfile(dateUtil.now())
            if (profile != null) {
                "${if (isFakeExtended) rh.gs(R.string.extended_bolus) else rh.gs(R.string.tempbasal_label)}: ${
                    tempBasal.toStringFull(profile, dateUtil, rh)
                }\n${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}"
            } else {
                rh.gs(R.string.confirm_remove_multiple_items, selected.size)
            }
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
                selected.forEach { tempBasal ->
                    val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
                    if (isFakeExtended) {
                        // For fake extended boluses, delete the underlying extended bolus
                        val extendedBolus = persistenceLayer.getExtendedBolusActiveAt(tempBasal.timestamp)
                        if (extendedBolus != null) {
                            persistenceLayer.invalidateExtendedBolus(
                                id = extendedBolus.id,
                                action = Action.EXTENDED_BOLUS_REMOVED,
                                source = Sources.Treatments,
                                listValues = listOf(
                                    ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                    ValueWithUnit.Insulin(extendedBolus.amount),
                                    ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt())
                                )
                            )
                        }
                    } else {
                        // Delete regular temp basal
                        persistenceLayer.invalidateTemporaryBasal(
                            id = tempBasal.id,
                            action = Action.TEMP_BASAL_REMOVED,
                            source = Sources.Treatments,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(tempBasal.timestamp),
                                if (tempBasal.isAbsolute) ValueWithUnit.UnitPerHour(tempBasal.rate) else ValueWithUnit.Percent(tempBasal.rate.toInt()),
                                ValueWithUnit.Minute(T.msecs(tempBasal.duration).mins().toInt())
                            )
                        )
                    }
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete temp basals", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting temp basals")) }
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
 * UI state for TempBasalScreen
 */
@Immutable
data class TempBasalUiState(
    val tempBasals: List<TB> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<TB> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
