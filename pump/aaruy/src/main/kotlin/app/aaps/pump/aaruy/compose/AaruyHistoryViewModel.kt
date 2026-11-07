package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.pump.PumpHistoryRecord
import app.aaps.core.ui.compose.pump.PumpHistoryType
import app.aaps.core.ui.compose.pump.PumpHistoryUiState
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.aaps.core.ui.R as CoreUiR

/** Types of the pump log, and [DAILY] for its daily totals, which come from their own list. */
object AaruyHistoryTypes {

    const val BASAL: Byte = 0
    const val TEMP_BASAL: Byte = 1
    const val BOLUS: Byte = 2
    const val ALARM: Byte = 3
    const val REWIND: Byte = 4
    const val PRIME: Byte = 5
    const val DAILY: Byte = 100
    const val REPLACED: Byte = 101
}

data class AaruyHistoryRecord(
    override val timestamp: Long,
    override val code: Byte,
    override val value: Double = 0.0,
    override val bolusType: String = "",
    override val stringValue: String = "",
    override val durationMinutes: Int = 0,
    override val dailyBasal: Double = 0.0,
    override val dailyBolus: Double = 0.0,
    override val alarm: String = ""
) : PumpHistoryRecord

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AaruyHistoryViewModel(
    private val rh: ResourceHelper,
    private val commandQueue: CommandQueue,
    private val aaruyPump: AaruyPump,
    private val historyDao: HistoryUnitInfoDao,
    private val dailyDao: HistoryDailyUnitInfoDao,
    private val dateUtil: DateUtil,
    private val ch: ConcentrationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PumpHistoryUiState<AaruyHistoryRecord>())
    val uiState: StateFlow<PumpHistoryUiState<AaruyHistoryRecord>> = _uiState

    init {
        val types = listOf(
            PumpHistoryType(AaruyHistoryTypes.BOLUS, rh.gs(R.string.bolus_title)),
            PumpHistoryType(AaruyHistoryTypes.TEMP_BASAL, rh.gs(R.string.temp_basal_title)),
            PumpHistoryType(AaruyHistoryTypes.BASAL, rh.gs(R.string.basal_rate_title)),
            PumpHistoryType(AaruyHistoryTypes.ALARM, rh.gs(R.string.history_alarm)),
            PumpHistoryType(AaruyHistoryTypes.REPLACED, rh.gs(R.string.replace_label)),
            PumpHistoryType(AaruyHistoryTypes.DAILY, rh.gs(R.string.daily_total))
        )
        _uiState.value = PumpHistoryUiState(availableTypes = types, selectedType = types.first())
        viewModelScope.launch { load(types.first().type) }
    }

    fun selectType(type: PumpHistoryType) {
        _uiState.update { it.copy(selectedType = type) }
        viewModelScope.launch { load(type.type) }
    }

    fun reload() {
        val type = _uiState.value.selectedType ?: return
        _uiState.update { it.copy(isLoading = true, statusMessage = "") }
        viewModelScope.launch {
            // Through the queue, which owns the link. Its status read ends when the log is written.
            commandQueue.readStatus(rh.gs(CoreUiR.string.user_request))
            load(type.type)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun load(type: Byte) {
        val since = dateUtil.now() - T.months(1).msecs()
        val records = withContext(Dispatchers.IO) {
            if (type == AaruyHistoryTypes.DAILY) dailyDao.queryAll().filter { it.dateTimestamp >= since }.map {
                AaruyHistoryRecord(timestamp = it.dateTimestamp, code = type, dailyBolus = it.dailyBolus / 10.0, dailyBasal = (it.dailyTotal - it.dailyBolus) / 10.0)
            }
            else historyDao.querySince(since).filter { matches(it, type) }.map { toRecord(it, type) }
        }
        // A slower read of the type chosen before must not replace the list of the type chosen now.
        // Equal rows would give equal list keys, and that crashes the list.
        _uiState.update { if (it.selectedType?.type != type) it else it.copy(records = records.distinct().sortedByDescending { record -> record.timestamp }) }
    }

    private fun matches(item: HistoryUnitInfo, type: Byte): Boolean =
        if (type == AaruyHistoryTypes.REPLACED) item.type == AaruyHistoryTypes.REWIND.toInt() || item.type == AaruyHistoryTypes.PRIME.toInt()
        else item.type == type.toInt()

    private fun toRecord(item: HistoryUnitInfo, type: Byte): AaruyHistoryRecord = when (type) {
        // Seen on a real pump: the log keeps only the low byte of the alarm.
        AaruyHistoryTypes.ALARM -> AaruyHistoryRecord(item.time, type, alarm = aaruyPump.alarmStateToString(AlarmState.fromHistory(item.data.toInt())))

        AaruyHistoryTypes.REPLACED -> AaruyHistoryRecord(
            item.time, type,
            stringValue = rh.gs(if (item.type == AaruyHistoryTypes.REWIND.toInt()) R.string.aaruy_rewind else R.string.actions_reservoir_prime)
        )

        // A bolus has an amount now and an extended part, in thousandths of a unit.
        else                       -> AaruyHistoryRecord(item.time, type, value = (item.data + item.delayBolus) / 1000.0, durationMinutes = item.injectTime.toInt())
    }

    fun timeText(record: AaruyHistoryRecord): String =
        if (record.code == AaruyHistoryTypes.DAILY) dateUtil.dateString(record.timestamp) else dateUtil.dateAndTimeString(record.timestamp)

    /** The pump counts in its own units. With insulin that is not U100 they differ from insulin units, so both are shown. */
    fun valueText(record: AaruyHistoryRecord): String = when (record.code) {
        AaruyHistoryTypes.ALARM    -> record.alarm
        AaruyHistoryTypes.REPLACED -> record.stringValue
        AaruyHistoryTypes.DAILY    -> ch.insulinAmountString(PumpInsulin(record.dailyBolus + record.dailyBasal))
        AaruyHistoryTypes.BOLUS    -> ch.insulinAmountString(PumpInsulin(record.value))
        else                       -> ch.basalRateString(PumpRate(record.value), true)
    }

    fun detailText(record: AaruyHistoryRecord): String = when (record.code) {
        AaruyHistoryTypes.DAILY -> rh.gs(
            R.string.aaruy_history_daily_parts,
            ch.insulinAmountString(PumpInsulin(record.dailyBolus)),
            ch.insulinAmountString(PumpInsulin(record.dailyBasal))
        )
        AaruyHistoryTypes.BASAL, AaruyHistoryTypes.TEMP_BASAL -> rh.gs(CoreUiR.string.format_mins, record.durationMinutes)
        else                    -> ""
    }
}
