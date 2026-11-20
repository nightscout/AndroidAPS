package app.aaps.pump.omnipod.omnipod5.ui.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.omnipod5.history.O5History
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.GregorianCalendar

@Stable
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class O5PodHistoryViewModel(
    private val o5History: O5History,
    private val aapsSchedulers: AapsSchedulers,
    val rh: ResourceHelper,
    val profileUtil: ProfileUtil
) : ViewModel() {

    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records

    init {
        val calendar = GregorianCalendar()
        calendar.add(Calendar.DAY_OF_MONTH, -5)
        _records.value = o5History.getRecordsAfter(calendar.timeInMillis)
            .subscribeOn(aapsSchedulers.io)
            .blockingGet()
    }
}
