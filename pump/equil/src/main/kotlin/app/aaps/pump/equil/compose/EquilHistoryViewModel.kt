package app.aaps.pump.equil.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.database.EquilHistoryPump
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.EquilHistoryEntryGroup
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.manager.Utils
import app.aaps.pump.equil.manager.command.PumpEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
@Stable
class EquilHistoryViewModel @Inject constructor(
    private val equilHistoryRecordDao: EquilHistoryRecordDao,
    private val equilHistoryPumpDao: EquilHistoryPumpDao,
    private val equilPumpPlugin: EquilPumpPlugin,
    private val dateUtil: DateUtil,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    val profileUtil: ProfileUtil,
) : ViewModel() {

    private val _selectedGroup = MutableStateFlow(EquilHistoryEntryGroup.All)
    val selectedGroup: StateFlow<EquilHistoryEntryGroup> = _selectedGroup

    private val _commandHistory = MutableStateFlow<List<EquilHistoryRecord>>(emptyList())

    val filteredCommandHistory: StateFlow<List<EquilHistoryRecord>> = combine(
        _commandHistory, _selectedGroup
    ) { records, group ->
        if (group == EquilHistoryEntryGroup.All) records
        else records.filter { it.type?.let(::groupForType) == group }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pumpEvents = MutableStateFlow<List<PumpEventItem>>(emptyList())
    val pumpEvents: StateFlow<List<PumpEventItem>> = _pumpEvents

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadData()
        rxBus.toFlow(EventEquilDataChanged::class.java)
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    fun setFilter(group: EquilHistoryEntryGroup) {
        _selectedGroup.value = group
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = fiveDaysAgoMidnight()
                val endTime = dateUtil.now()
                val serialNumber = equilPumpPlugin.serialNumber()

                val records = async { equilHistoryRecordDao.allSince(startTime, endTime) }
                val pump = async { equilHistoryPumpDao.allFromByType(startTime, endTime, serialNumber) }

                _commandHistory.value = records.await()
                _pumpEvents.value = transformPumpEvents(pump.await())
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "Failed to load equil history", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fiveDaysAgoMidnight(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -5)
        }.timeInMillis

    // region Tab 2: pump event transformation

    private fun transformPumpEvents(rawList: List<EquilHistoryPump>): List<PumpEventItem> {
        val sorted = rawList.sortedWith(compareBy(EquilHistoryPump::eventTimestamp, EquilHistoryPump::eventIndex))
        val result = mutableListOf<PumpEventItem>()

        var lastBasalRate: Int? = null
        var bolusStart: EquilHistoryPump? = null
        var previousEntry: EquilHistoryPump? = null

        for (entry in sorted) {
            // Basal rate change
            if (lastBasalRate == null || lastBasalRate != entry.rate) {
                val isTemp = previousEntry?.type == PUMP_EVENT_TYPE_TEMP_BASAL
                result += PumpEventItem.BasalChange(
                    timestamp = entry.eventTimestamp,
                    rateUH = Utils.decodeSpeedToUH(entry.rate),
                    isTemporary = isTemp
                )
                lastBasalRate = entry.rate
            }

            // Bolus: detect end (largeRate changed from >0 to different value)
            if (bolusStart != null && entry.largeRate != bolusStart.largeRate) {
                val durationSec = abs(entry.eventTimestamp - bolusStart.eventTimestamp) / 1000.0
                val delivered = durationSec * Utils.decodeSpeedToUS(bolusStart.largeRate)
                result += PumpEventItem.Bolus(
                    timestamp = bolusStart.eventTimestamp,
                    amountU = "%.3f".format(delivered)
                )
                bolusStart = null
            }

            previousEntry = entry
            if (entry.largeRate > 0) bolusStart = entry

            // Hardware/alarm events
            val eventRes = PumpEvent.getEventStringRes(entry.port, entry.type, entry.level)
            if (eventRes != null) {
                result += PumpEventItem.Event(
                    timestamp = entry.eventTimestamp,
                    descriptionRes = eventRes
                )
            }
        }
        return result.sortedByDescending { it.timestamp }
    }

    // endregion

    companion object {

        private const val PUMP_EVENT_TYPE_TEMP_BASAL = 10

        fun groupForType(type: EquilHistoryRecord.EventType): EquilHistoryEntryGroup = when (type) {
            EquilHistoryRecord.EventType.INITIALIZE_EQUIL,
            EquilHistoryRecord.EventType.INSERT_CANNULA,
            EquilHistoryRecord.EventType.UNPAIR_EQUIL            -> EquilHistoryEntryGroup.Pair

            EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL,
            EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL,
            EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS,
            EquilHistoryRecord.EventType.CANCEL_EXTENDED_BOLUS,
            EquilHistoryRecord.EventType.SET_BASAL_PROFILE,
            EquilHistoryRecord.EventType.RESUME_DELIVERY,
            EquilHistoryRecord.EventType.SUSPEND_DELIVERY        -> EquilHistoryEntryGroup.Basal

            EquilHistoryRecord.EventType.SET_BOLUS,
            EquilHistoryRecord.EventType.CANCEL_BOLUS            -> EquilHistoryEntryGroup.Bolus

            EquilHistoryRecord.EventType.SET_TIME,
            EquilHistoryRecord.EventType.SET_ALARM_MUTE,
            EquilHistoryRecord.EventType.SET_ALARM_SHAKE,
            EquilHistoryRecord.EventType.SET_ALARM_TONE,
            EquilHistoryRecord.EventType.SET_ALARM_TONE_AND_SHAK -> EquilHistoryEntryGroup.Configuration

            else                                                 -> EquilHistoryEntryGroup.All
        }

        fun failureStringRes(status: ResolvedResult?): Int = when (status) {
            ResolvedResult.NOT_FOUNT     -> R.string.equil_command_not_found
            ResolvedResult.CONNECT_ERROR -> R.string.equil_command_connect_error
            ResolvedResult.FAILURE       -> R.string.equil_command_connect_no_response
            ResolvedResult.SUCCESS       -> R.string.equil_success
            ResolvedResult.NONE          -> R.string.equil_none
            else                         -> R.string.equil_command__unknown
        }
    }
}

@Immutable
sealed class PumpEventItem {

    abstract val timestamp: Long

    data class BasalChange(
        override val timestamp: Long,
        val rateUH: Float,
        val isTemporary: Boolean,
    ) : PumpEventItem()

    data class Bolus(
        override val timestamp: Long,
        val amountU: String,
    ) : PumpEventItem()

    data class Event(
        override val timestamp: Long,
        @StringRes val descriptionRes: Int,
    ) : PumpEventItem()
}
