package app.aaps.plugins.source.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.clientcontrol.RecordRemoval
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for BgSourceScreen managing blood glucose readings state and business logic.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class BgSourceViewModel(
    private val persistenceLayer: PersistenceLayer,
    internal val rh: TextResolver,
    internal val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    batchExecutor: BatchExecutor,
    config: Config
) : ViewModel() {

    private val _uiState = MutableStateFlow(BgSourceUiState())
    val uiState: StateFlow<BgSourceUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch(aapsIoDispatcher) {
            // Only show loading on initial load, not on refreshes
            val currentState = uiState.value
            if (currentState.glucoseValues.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }

            try {
                val now = Clock.System.now().toEpochMilliseconds()
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

                _uiState.update {
                    it.copy(
                        glucoseValues = data,
                        duplicateIds = duplicateIds,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load BG data", e)
                _uiState.update { it.copy(isLoading = false) }
                rxBus.send(EventShowSnackbar(e.message ?: "Unknown error loading BG data", EventShowSnackbar.Type.Error))
            }
        }
    }

    /**
     * Load more historical data (for infinite scroll)
     */
    fun loadMoreData() {
        _uiState.update { it.copy(historyHours = it.historyHours + 24L) }
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
     * Format glucose value to string in user's preferred units
     */
    fun formatGlucoseValue(value: Double): String = profileUtil.fromMgdlToStringInUnits(value)

    /**
     * Enter selection mode with initial item selected
     */
    fun enterSelectionMode(item: GV) {
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
    fun toggleSelection(item: GV) {
        _uiState.update { state ->
            val newSelection = if (item in state.selectedItems) {
                state.selectedItems - item
            } else {
                state.selectedItems + item
            }
            state.copy(selectedItems = newSelection)
        }
    }

    /**
     * Removing the selected readings. The master removes its own copies: a BG reading removed only on a client
     * would still be used by the loop on the master.
     */
    val removal = RecordRemoval(batchExecutor, rh, rxBus, config, viewModelScope, Sources.BgFragment)

    /** Ask the master to describe removing the selected readings; the screen then shows its confirmation. */
    fun requestDelete() {
        removal.request(listOfNotNull(BatchAction.Remove.of(BatchAction.Remove.Kind.GLUCOSE_VALUE, uiState.value.selectedItems)))
    }

    /** The master removed them. */
    fun onRemoved() {
        exitSelectionMode()
        loadData()
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
    val historyHours: Long = 36L
)
