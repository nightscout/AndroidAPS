package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.extensions.toTemporaryBasal
import app.aaps.core.ui.clientcontrol.RecordRemoval
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.compose.treatments.viewmodels.TreatmentConstants.TREATMENT_HISTORY_DAYS
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for TempBasalScreen managing temporary basal state and business logic.
 */
@Stable
@Inject
class TempBasalViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    val rh: TextResolver,
    val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    batchExecutor: BatchExecutor,
    config: Config
) : ViewModel() {

    private val _uiState = MutableStateFlow(TempBasalUiState())
    val uiState: StateFlow<TempBasalUiState> = _uiState.asStateFlow()

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
                _uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = dateUtil.now()
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
                            // The converted row is built here and has no id of its own, so every one was 0
                            // and two of them crashed the list with a duplicate key. Give it the extended
                            // bolus id; TempBasalScreen keys these rows apart from real temp basals.
                            eb.toTemporaryBasal(profile).copy(id = eb.id)
                        }
                    }

                    (tempBasalsList + convertedExtendedBoluses).sortedByDescending { it.timestamp }
                } else {
                    tempBasalsList.sortedByDescending { it.timestamp }
                }

                _uiState.update {
                    it.copy(
                        tempBasals = tempBasals,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load temp basals", e)
                _uiState.update { it.copy(isLoading = false) }
                rxBus.send(EventShowSnackbar(e.message ?: "Unknown error loading temp basals", EventShowSnackbar.Type.Error))
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
     * Toggle show/hide invalidated items
     */
    fun toggleInvalidated() {
        _uiState.update { it.copy(showInvalidated = !it.showInvalidated) }
        loadData()
    }

    /**
     * Enter selection mode with initial item selected
     */
    fun enterSelectionMode(item: TB) {
        _uiState.update {
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
        _uiState.update {
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
        _uiState.update { state ->
            val newSelection = if (item in state.selectedItems) {
                state.selectedItems - item
            } else {
                state.selectedItems + item
            }
            state.copy(selectedItems = newSelection)
        }
    }

    /** Removing the selected items: the master describes and removes them, on every role. */
    val removal = RecordRemoval(batchExecutor, rh, rxBus, config, viewModelScope, Sources.Treatments)

    /** Ask the master to describe removing the selected items; the screen then shows its confirmation. */
    fun requestDelete() {
        val selected = uiState.value.selectedItems
        viewModelScope.launch {
            // An extended bolus that stands in for a temp basal is listed here as one; removing it removes the
            // extended bolus behind it.
            val (faked, regular) = selected.partition { it.type == TB.Type.FAKE_EXTENDED }
            val extended = faked.mapNotNull { persistenceLayer.getExtendedBolusActiveAt(it.timestamp) }
            removal.request(
                listOfNotNull(
                    BatchAction.Remove.of(BatchAction.Remove.Kind.TEMPORARY_BASAL, regular),
                    BatchAction.Remove.of(BatchAction.Remove.Kind.EXTENDED_BOLUS, extended)
                )
            )
        }
    }

    /** The master removed them. */
    fun onRemoved() {
        exitSelectionMode()
        loadData()
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
    val selectedItems: Set<TB> = emptySet()
)
