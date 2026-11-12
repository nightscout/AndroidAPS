package app.aaps.pump.ap550s.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.pump.PumpHistoryType
import app.aaps.core.ui.compose.pump.PumpHistoryUiState
import app.aaps.pump.ap550.protocol.comm.RecordTypes
import app.aaps.pump.ap550s.AP550Pump
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.database.Ap550HistoryRecord
import app.aaps.pump.ap550s.database.Ap550HistoryRecordDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pump history screen for the AP550 driver.
 *
 * The record types the pump can report are fixed, so the type list is built once. Reading the
 * history from the pump is a separate step ([reload]): it asks the command queue to fetch the
 * records, which the execution service writes into the Room table, and only then reads them back.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class Ap550HistoryViewModel(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    private val ap550HistoryRecordDao: Ap550HistoryRecordDao,
    private val ap550Pump: AP550Pump,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpHistoryUiState<Ap550HistoryRecord>())
    val uiState: StateFlow<PumpHistoryUiState<Ap550HistoryRecord>> = _uiState

    init {
        // Carbohydrates and the record types the pump never reports are left out, matching the
        // driver's own list.
        val types = listOf(
            PumpHistoryType(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.ap550_history_alarm)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.ap550_history_basalhours)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.ap550_history_bolus)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.ap550_history_dailyinsulin)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.ap550_history_refill)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_PARAMMODIFY, rh.gs(R.string.ap550_history_param_modify)),
            PumpHistoryType(RecordTypes.RECORD_TYPE_BASALMODIFY, rh.gs(R.string.ap550_history_basal_modify))
        )

        _uiState.value = PumpHistoryUiState(availableTypes = types, selectedType = types.firstOrNull())

        // viewModelScope is Main, like observeOn(aapsSchedulers.main), and dies with the view model
        // like the CompositeDisposable did. UNDISPATCHED because RxBus has no replay, so a scheduled
        // collector could miss a status sent before it starts.
        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                _uiState.update { it.copy(statusMessage = rh.gs(event.getStatus())) }
            }

        types.firstOrNull()?.let { type -> viewModelScope.launch { loadRecords(type.type) } }
    }

    fun selectType(type: PumpHistoryType) {
        _uiState.update { it.copy(selectedType = type) }
        viewModelScope.launch { loadRecords(type.type) }
    }

    /**
     * Fetches the selected record type from the pump, then reads it back from the database.
     *
     * A pump that runs firmware older than DCN18 cannot report basal or parameter changes at all,
     * so asking for them is refused with a message instead of starting a transfer that would fail.
     */
    fun reload() {
        val type = _uiState.value.selectedType ?: return
        if (ap550Pump.pumpVersionString == rh.gs(R.string.ap550_version_before_DCN18) &&
            (type.type == RecordTypes.RECORD_TYPE_BASALMODIFY || type.type == RecordTypes.RECORD_TYPE_PARAMMODIFY)
        ) {
            rxBus.send(EventShowSnackbar(rh.gs(R.string.ap550_pump_version_too_low), EventShowSnackbar.Type.Warning))
            return
        }
        _uiState.update { it.copy(isLoading = true, statusMessage = "") }
        viewModelScope.launch {
            commandQueue.loadHistory(type.type)
            loadRecords(type.type)
            _uiState.update { it.copy(isLoading = false, statusMessage = "") }
        }
    }

    /** Timestamp shown for a record. The daily record covers a whole day, so it shows the date only. */
    fun formatTime(record: Ap550HistoryRecord): String {
        val type = _uiState.value.selectedType?.type ?: return ""
        return when (type) {
            RecordTypes.RECORD_TYPE_DAILY -> dateUtil.dateString(record.timestamp)
            else                          -> dateUtil.dateAndTimeString(record.timestamp)
        }
    }

    fun formatBolus(record: Ap550HistoryRecord): String = rh.gs(R.string.format_insulin_units_3, record.value)

    /**
     * Temp basal value. The pump reports "1" in [Ap550HistoryRecord.stringValue] when the record is a
     * percentage, and "2" (or anything else) when it is an absolute rate.
     */
    fun formatTempBasal(record: Ap550HistoryRecord): String =
        if (record.stringValue == "1") rh.gs(R.string.format_tb_percent_units, record.value.toInt())
        else rh.gs(R.string.format_tb_rate_units, record.value)

    fun formatDuration(record: Ap550HistoryRecord): String = rh.gs(R.string.format_duration_units, record.durationMinutes)

    fun formatDailyBasal(record: Ap550HistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBasal)

    fun formatDailyBolus(record: Ap550HistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus)

    fun formatDailyTotal(record: Ap550HistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus + record.dailyBasal)

    fun formatValue(record: Ap550HistoryRecord): String = decimalFormatter.to3Decimal(record.value)

    // Room runs the suspend query on its own executor, so no dispatcher switch is needed here.
    private suspend fun loadRecords(type: Byte) {
        try {
            val records = ap550HistoryRecordDao.allFromByType(dateUtil.now() - T.months(120).msecs(), type)
            _uiState.update { it.copy(records = records) }
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error loading AP550 history", e)
        }
    }
}