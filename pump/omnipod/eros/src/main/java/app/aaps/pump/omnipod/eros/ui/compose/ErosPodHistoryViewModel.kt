package app.aaps.pump.omnipod.eros.ui.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.eros.history.ErosHistory
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.GregorianCalendar
import dev.zacsweers.metro.Inject

@Stable
// Registers itself: @ViewModelKey infers the key from the class. Deliberately unscoped, so each screen
// gets its own - the same shape the other pump view models use.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
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
