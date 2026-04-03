package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.compose.treatments.MealLink
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.TREATMENT_HISTORY_DAYS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for BolusCarbsScreen managing bolus, carbs, and calculator result state and business logic.
 */
@Stable
class BolusCarbsViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger
) : ViewModel() {

    val uiState: StateFlow<BolusCarbsUiState>
        field = MutableStateFlow(BolusCarbsUiState())

    init {
        loadData()
        observeTreatmentChanges()
    }

    /**
     * Load meal links (boluses, carbs, and calculator results)
     */
    fun loadData() {
        viewModelScope.launch {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.mealLinks.isEmpty()) {
                uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = System.currentTimeMillis()
                val millsToThePast = T.days(TREATMENT_HISTORY_DAYS).msecs()

                val boluses = if (currentState.showInvalidated) {
                    persistenceLayer.getBolusesFromTimeIncludingInvalid(now - millsToThePast, false)
                        .map { MealLink(bolus = it) }
                } else {
                    persistenceLayer.getBolusesFromTime(now - millsToThePast, false)
                        .map { MealLink(bolus = it) }
                }

                val carbs = if (currentState.showInvalidated) {
                    persistenceLayer.getCarbsFromTimeIncludingInvalid(now - millsToThePast, false)
                        .map { MealLink(carbs = it) }
                } else {
                    persistenceLayer.getCarbsFromTime(now - millsToThePast, false)
                        .map { MealLink(carbs = it) }
                }

                val calcs = if (currentState.showInvalidated) {
                    persistenceLayer.getBolusCalculatorResultsIncludingInvalidFromTime(now - millsToThePast, false)
                        .map { MealLink(bolusCalculatorResult = it) }
                } else {
                    persistenceLayer.getBolusCalculatorResultsFromTime(now - millsToThePast, false)
                        .map { MealLink(bolusCalculatorResult = it) }
                }

                val mealLinks = (boluses + carbs + calcs).sortedByDescending {
                    it.bolusCalculatorResult?.timestamp ?: it.bolus?.timestamp ?: it.carbs?.timestamp ?: 0L
                }

                uiState.update {
                    it.copy(
                        mealLinks = mealLinks,
                        isLoading = false,
                        snackbarMessage = null
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load bolus/carbs data", e)
                uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error loading bolus/carbs data")
                    )
                }
            }
        }
    }

    /**
     * Subscribe to treatment change events using Flow
     * Observes Bolus, Carbs, and BolusCalculatorResult changes
     */
    @OptIn(FlowPreview::class)
    private fun observeTreatmentChanges() {
        merge(
            persistenceLayer.observeChanges<BS>(),
            persistenceLayer.observeChanges<CA>(),
            persistenceLayer.observeChanges<BCR>()
        )
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
    fun enterSelectionMode(item: MealLink) {
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
    fun toggleSelection(item: MealLink) {
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
     * Get current profile
     */
    suspend fun getProfile() = profileFunction.getProfile()

    /**
     * Prepare delete confirmation message
     */
    fun getDeleteConfirmationMessage(): String {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return ""

        return if (selected.size == 1) {
            val ml = selected.first()
            val bolus = ml.bolus
            if (bolus != null) {
                "${rh.gs(R.string.configbuilder_insulin)}: ${rh.gs(R.string.format_insulin_units, bolus.amount)}\n${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(bolus.timestamp)}"
            } else {
                val carbs = ml.carbs
                if (carbs != null) {
                    "${rh.gs(R.string.carbs)}: ${rh.gs(app.aaps.core.objects.R.string.format_carbs, carbs.amount.toInt())}\n${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(carbs.timestamp)}"
                } else {
                    rh.gs(R.string.confirm_remove_multiple_items, selected.size)
                }
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
                selected.forEach { ml ->
                    ml.bolus?.let { bolus ->
                        persistenceLayer.invalidateBolus(
                            bolus.id,
                            action = Action.BOLUS_REMOVED,
                            source = Sources.Treatments,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(bolus.timestamp),
                                ValueWithUnit.Insulin(bolus.amount)
                            )
                        )
                    }
                    ml.carbs?.let { carb ->
                        persistenceLayer.invalidateCarbs(
                            carb.id,
                            action = Action.CARBS_REMOVED,
                            source = Sources.Treatments,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(carb.timestamp),
                                ValueWithUnit.Gram(carb.amount.toInt())
                            )
                        )
                    }
                    ml.bolusCalculatorResult?.let { bolusCalculatorResult ->
                        persistenceLayer.invalidateBolusCalculatorResult(
                            bolusCalculatorResult.id,
                            action = Action.BOLUS_CALCULATOR_RESULT_REMOVED,
                            source = Sources.Treatments,
                            listValues = listOf(ValueWithUnit.Timestamp(bolusCalculatorResult.timestamp))
                        )
                    }
                }
                exitSelectionMode()
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete treatments", e)
                uiState.update { it.copy(snackbarMessage = SnackbarMessage.Error(e.message ?: "Unknown error deleting treatments")) }
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
 * UI state for BolusCarbsScreen
 */
@Immutable
data class BolusCarbsUiState(
    val mealLinks: List<MealLink> = emptyList(),
    val isLoading: Boolean = true,
    val showInvalidated: Boolean = false,
    val isRemovingMode: Boolean = false,
    val selectedItems: Set<MealLink> = emptySet(),
    val snackbarMessage: SnackbarMessage? = null
)
