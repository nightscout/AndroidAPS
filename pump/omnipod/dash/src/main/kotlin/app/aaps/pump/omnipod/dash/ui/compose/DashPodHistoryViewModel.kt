package app.aaps.pump.omnipod.dash.ui.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.HistoryRecord
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.GregorianCalendar
import javax.inject.Inject

@Stable
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
class DashPodHistoryViewModel @Inject constructor(
    private val dashHistory: DashHistory,
    private val aapsSchedulers: AapsSchedulers,
    val rh: ResourceHelper,
    val profileUtil: ProfileUtil
) : ViewModel() {

    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val gc = GregorianCalendar()
        gc.add(Calendar.DAY_OF_MONTH, -DAYS_TO_DISPLAY)
        val records = dashHistory.getRecordsAfter(gc.timeInMillis)
            .subscribeOn(aapsSchedulers.io)
            .blockingGet()
        _records.value = records
    }

    companion object {

        private const val DAYS_TO_DISPLAY = 5
    }
}
