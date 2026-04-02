package app.aaps.pump.medtronic.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class MedtronicHistoryUiState(
    val selectedGroup: PumpHistoryEntryGroup = PumpHistoryEntryGroup.All,
    val records: List<PumpHistoryEntry> = emptyList()
)

@Stable
@HiltViewModel
class MedtronicHistoryViewModel @Inject constructor(
    val rh: ResourceHelper,
    private val medtronicHistoryData: MedtronicHistoryData
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedtronicHistoryUiState())
    val uiState: StateFlow<MedtronicHistoryUiState> = _uiState

    val groups: List<PumpHistoryEntryGroup> = PumpHistoryEntryGroup.getTranslatedList(rh)

    init {
        filterHistory(PumpHistoryEntryGroup.All)
    }

    fun selectGroup(group: PumpHistoryEntryGroup) {
        filterHistory(group)
    }

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        val all = medtronicHistoryData.allHistory
        val filtered = if (group == PumpHistoryEntryGroup.All) all
        else all.filter { it.entryType.group == group }
        _uiState.update { it.copy(selectedGroup = group, records = filtered) }
    }
}
