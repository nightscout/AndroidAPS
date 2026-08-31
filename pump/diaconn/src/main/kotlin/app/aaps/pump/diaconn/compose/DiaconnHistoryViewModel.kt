package app.aaps.pump.diaconn.compose

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.pump.PumpHistoryType
import app.aaps.core.ui.compose.pump.PumpHistoryUiState
import app.aaps.pump.diaconn.R
import app.aaps.pump.diaconn.common.RecordTypes
import app.aaps.pump.diaconn.database.DiaconnHistoryRecord
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class DiaconnHistoryViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    private val diaconnHistoryRecordDao: DiaconnHistoryRecordDao,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpHistoryUiState<DiaconnHistoryRecord>())
    val uiState: StateFlow<PumpHistoryUiState<DiaconnHistoryRecord>> = _uiState

    private val disposable = CompositeDisposable()

    init {
        val types = listOf(
            PumpHistoryType(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.diaconn_g8_history_alarm)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.diaconn_g8_history_basalhours)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.diaconn_g8_history_bolus)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_TB, rh.gs(R.string.diaconn_g8_history_tempbasal)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.diaconn_g8_history_dailyinsulin)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.diaconn_g8_history_refill)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_SUSPEND, rh.gs(R.string.diaconn_g8_history_suspend))
        )

        _uiState.value = PumpHistoryUiState(availableTypes = types, selectedType = types.firstOrNull())

        // viewModelScope is Main, like observeOn(aapsSchedulers.main), and dies with the view model
        // like the CompositeDisposable did. UNDISPATCHED because RxBus has no replay, so a scheduled
        // collector could miss a status sent before it starts.
        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                _uiState.update { it.copy(statusMessage = rh.gs(event.getStatus())) }
            }

        types.firstOrNull()?.let { loadRecords(it.type) }
    }

    fun selectType(type: PumpHistoryType) {
        _uiState.update { it.copy(selectedType = type) }
        loadRecords(type.type)
    }

    fun reload() {
        val type = _uiState.value.selectedType ?: return
        _uiState.update { it.copy(isLoading = true, statusMessage = "") }
        viewModelScope.launch {
            commandQueue.loadHistory(type.type)
            loadRecords(type.type)
            _uiState.update { it.copy(isLoading = false, statusMessage = "") }
        }
    }

    fun formatValue(record: DiaconnHistoryRecord): String =
        decimalFormatter.to2Decimal(record.value)

    fun formatTime(record: DiaconnHistoryRecord): String {
        val type = _uiState.value.selectedType?.type ?: return ""
        return when (type) {
            RecordTypes.RECORD_TYPE_DAILY -> dateUtil.dateString(record.timestamp)
            else                          -> dateUtil.dateAndTimeString(record.timestamp)
        }
    }

    fun formatDailyTotal(record: DiaconnHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus + record.dailyBasal)

    fun formatDailyBolus(record: DiaconnHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus)

    fun formatDailyBasal(record: DiaconnHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBasal)

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    private fun loadRecords(type: Byte) {
        disposable += diaconnHistoryRecordDao
            .allFromByType(dateUtil.now() - T.months(1).msecs(), type)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ records ->
                           _uiState.update { it.copy(records = records) }
                       }, { aapsLogger.error(LTag.PUMP, "Error loading history", it) })
    }
}
