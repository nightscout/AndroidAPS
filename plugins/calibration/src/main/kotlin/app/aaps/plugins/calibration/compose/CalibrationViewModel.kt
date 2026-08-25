package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.calibration.fitLinearCalibration
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.launch
import javax.inject.Inject

private val WARM_UP_DURATION_MS = T.hours(2).msecs()

@HiltViewModel
@Stable
class CalibrationViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val aapsLogger: AAPSLogger,
    val dateUtil: DateUtil
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

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            val entry = _uiState.value.entries.firstOrNull { it.id == id }
            try {
                // UserEntry logging is performed inside PersistenceLayer.invalidateCalibrationEntry
                persistenceLayer.invalidateCalibrationEntry(
                    id = id,
                    action = Action.CALIBRATION_REMOVED,
                    source = Sources.CalibrationDialog,
                    note = "id=$id",
                    listValues = entry?.let {
                        listOf(
                            ValueWithUnit.fromGlucoseUnit(
                                profileUtil.fromMgdlToUnits(it.fingerstickMgdl),
                                profileUtil.units
                            )
                        )
                    } ?: emptyList()
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.DATABASE, "Failed to invalidate calibration entry id=$id", e)
            }
        }
    }

    fun selectEntry(id: Long) {
        _uiState.update { it.copy(selectedEntryId = id) }
    }

}
