package app.aaps.pump.omnipod.eros.ui.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

@Stable
@HiltViewModel
class ErosPodHistoryViewModel @Inject constructor(
    private val erosHistory: ErosHistory,
    val aapsOmnipodUtil: AapsOmnipodUtil,
    val rh: ResourceHelper,
    val profileUtil: ProfileUtil
) : ViewModel() {

    private val _records = MutableStateFlow<List<ErosHistoryRecordEntity>>(emptyList())
    val records: StateFlow<List<ErosHistoryRecordEntity>> = _records

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val gc = GregorianCalendar()
        gc.add(Calendar.HOUR_OF_DAY, -24)
        val records = erosHistory.getAllErosHistoryRecordsFromTimestamp(gc.timeInMillis)
        _records.value = records.sorted()
    }
}
