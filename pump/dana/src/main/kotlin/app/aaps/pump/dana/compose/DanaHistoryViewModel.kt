package app.aaps.pump.dana.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.pump.PumpHistoryType
import app.aaps.core.ui.compose.pump.PumpHistoryUiState
import app.aaps.pump.dana.R
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecord
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.events.EventDanaRSyncStatus
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
import dev.zacsweers.metro.Inject

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class DanaHistoryViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val danaHistoryRecordDao: DanaHistoryRecordDao,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val profileUtil: ProfileUtil,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpHistoryUiState<DanaHistoryRecord>())
    val uiState: StateFlow<PumpHistoryUiState<DanaHistoryRecord>> = _uiState

    private val disposable = CompositeDisposable()

    init {
        val pump = activePlugin.activePump
        val isKorean = pump.pumpDescription.pumpType == PumpType.DANA_R_KOREAN
        val isRS = pump.pumpDescription.pumpType == PumpType.DANA_RS || pump.pumpDescription.pumpType == PumpType.DANA_I

        val types = buildList {
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.danar_history_alarm)))
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.danar_history_basalhours)))
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.danar_history_bolus)))
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_CARBO, rh.gs(R.string.danar_history_carbohydrates)))
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.danar_history_dailyinsulin)))
            add(PumpHistoryType(RecordTypes.RECORD_TYPE_GLUCOSE, rh.gs(R.string.danar_history_glucose)))
            if (!isKorean && !isRS) add(PumpHistoryType(RecordTypes.RECORD_TYPE_ERROR, rh.gs(app.aaps.core.ui.R.string.errors)))
            if (isRS) add(PumpHistoryType(RecordTypes.RECORD_TYPE_PRIME, rh.gs(R.string.danar_history_prime)))
            if (!isKorean) {
                add(PumpHistoryType(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.danar_history_refill)))
                add(PumpHistoryType(RecordTypes.RECORD_TYPE_SUSPEND, rh.gs(R.string.danar_history_syspend)))
            }
        }

        _uiState.value = PumpHistoryUiState(availableTypes = types, selectedType = types.firstOrNull())

        // Listen for sync status. viewModelScope is Main, like observeOn(aapsSchedulers.main), and
        // dies with the view model like the CompositeDisposable did. UNDISPATCHED because RxBus has
        // no replay, so a scheduled collector could miss a status sent before it starts.
        rxBus.toFlow(EventDanaRSyncStatus::class)
            .collectResilient(viewModelScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                _uiState.update { it.copy(statusMessage = event.message) }
            }

        // Load initial data
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

    fun formatValue(record: DanaHistoryRecord): String {
        val type = _uiState.value.selectedType?.type ?: return ""
        return when (type) {
            RecordTypes.RECORD_TYPE_GLUCOSE -> profileUtil.fromMgdlToStringInUnits(record.value)
            else                            -> decimalFormatter.to2Decimal(record.value)
        }
    }

    fun formatTime(record: DanaHistoryRecord): String {
        val type = _uiState.value.selectedType?.type ?: return ""
        return when (type) {
            RecordTypes.RECORD_TYPE_DAILY -> dateUtil.dateString(record.timestamp)
            else                          -> dateUtil.dateAndTimeString(record.timestamp)
        }
    }

    fun formatDailyTotal(record: DanaHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus + record.dailyBasal)

    fun formatDailyBolus(record: DanaHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBolus)

    fun formatDailyBasal(record: DanaHistoryRecord): String =
        rh.gs(app.aaps.core.interfaces.R.string.format_insulin_units, record.dailyBasal)

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }

    private fun loadRecords(type: Byte) {
        disposable += danaHistoryRecordDao
            .allFromByType(dateUtil.now() - T.months(1).msecs(), type)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe({ records ->
                           _uiState.update { it.copy(records = records) }
                       }, { aapsLogger.error(LTag.PUMP, "Error loading history", it) })
    }
}
