package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.clientcontrol.RecordRemoval
import app.aaps.plugins.calibration.fitLinearCalibration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

private val WARM_UP_DURATION_MS = T.hours(2).msecs()

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own. Injected rather than built by the plugin, so the plugin does
// not need the batch path (which reaches the plugin again through ActivePlugin).
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class CalibrationViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    val dateUtil: DateUtil,
    rh: TextResolver,
    rxBus: RxBus,
    config: Config,
    batchExecutor: BatchExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        observeChanges()
    }

    // mapLatest cancels an in-flight recompute when a new emission arrives, so
    // out-of-order completions can't deliver a stale snapshot to _uiState. onStart
    // injects an initial trigger so the first recompute fires at construction.
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeChanges() {
        merge(
            persistenceLayer.observeChanges<CAL>().map { },
            persistenceLayer.observeChanges<TE>().map { }
        )
            .onStart { emit(Unit) }
            .debounce(500L)
            .mapLatest { recomputeSuspend() }
            .launchIn(viewModelScope)
    }

    private suspend fun recomputeSuspend() {
        val now = dateUtil.now()
        val sessionStart = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp
        val entries = if (sessionStart != null) persistenceLayer.getValidCalibrationEntriesSince(sessionStart) else emptyList()
        val warmUpEndsAt = sessionStart?.plus(WARM_UP_DURATION_MS)
        val isInWarmUp = warmUpEndsAt != null && now < warmUpEndsAt
        _uiState.update { previous ->
            val previousSelectionStillPresent = previous.selectedEntryId != null &&
                entries.any { it.id == previous.selectedEntryId }
            val selectedEntryId = when {
                previousSelectionStillPresent -> previous.selectedEntryId
                else                          -> entries.lastOrNull()?.id
            }
            CalibrationUiState(
                sessionStart = sessionStart,
                warmUpEndsAt = warmUpEndsAt,
                isInWarmUp = isInWarmUp,
                entries = entries,
                fit = fitLinearCalibration(entries, now),
                now = now,
                selectedEntryId = selectedEntryId,
                glucoseUnit = profileUtil.units
            )
        }
    }

    /**
     * Removing an entry. The master removes its own copy: a calibration removed only on a client would still
     * correct the BG the loop uses on the master.
     */
    val removal = RecordRemoval(batchExecutor, rh, rxBus, config, viewModelScope, Sources.CalibrationDialog)

    /** Ask the master to describe removing the entry [id]; the screen then shows its confirmation. */
    fun requestDelete(id: Long) {
        val entry = _uiState.value.entries.firstOrNull { it.id == id } ?: return
        removal.request(listOfNotNull(BatchAction.Remove.of(BatchAction.Remove.Kind.CALIBRATION, listOf(entry))))
    }

    fun selectEntry(id: Long) {
        _uiState.update { it.copy(selectedEntryId = id) }
    }

}
