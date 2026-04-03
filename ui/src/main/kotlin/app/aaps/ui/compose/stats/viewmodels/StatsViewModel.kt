package app.aaps.ui.compose.stats.viewmodels

import androidx.collection.LongSparseArray
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TDD
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.activityMonitor.ActivityStats
import app.aaps.ui.compose.stats.CycleSeries
import app.aaps.ui.compose.stats.TddCyclePatternData
import app.aaps.ui.compose.stats.TddStatsData
import app.aaps.ui.compose.stats.TirStatsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for StatsScreen managing statistics data loading and state.
 */
@HiltViewModel
@Stable
class StatsViewModel @Inject constructor(
    private val tddCalculator: TddCalculator,
    private val tirCalculator: TirCalculator,
    private val dexcomTirCalculator: DexcomTirCalculator,
    private val activityMonitor: ActivityMonitor,
    private val persistenceLayer: PersistenceLayer,
    val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    val dateUtil: DateUtil,
    val profileUtil: ProfileUtil,
    private val preferences: Preferences
) : ViewModel() {

    val uiState: StateFlow<StatsUiState>
        field = MutableStateFlow(StatsUiState())

    /** Cached daily TDD entries sorted by timestamp descending (newest first) for cycle computation */
    private var cachedDailyTdds: List<TDD> = emptyList()
    private var cycleLoadJob: kotlinx.coroutines.Job? = null

    init {
        val savedOffset = preferences.get(IntNonKey.TddCycleOffset)
        uiState.update { it.copy(tddCycleOffset = savedOffset) }
        loadAllStats()
    }

    private fun loadAllStats() {
        loadTddStats()
        loadTirStats()
        loadDexcomTirStats()
        loadActivityStats()
    }

    private fun loadTddStats() {
        viewModelScope.launch {
            uiState.update { it.copy(tddLoading = true) }
            val data = withContext(Dispatchers.IO) {
                val tdds = tddCalculator.calculate(7, allowMissingDays = true)
                val averageTdd = tddCalculator.averageTDD(tdds)
                val todayTdd = tddCalculator.calculateToday()
                TddStatsData(tdds = tdds, averageTdd = averageTdd, todayTdd = todayTdd)
            }
            uiState.update { it.copy(tddStatsData = data, tddLoading = false) }
        }
    }

    private fun loadTirStats() {
        viewModelScope.launch {
            uiState.update { it.copy(tirLoading = true) }
            val data = withContext(Dispatchers.IO) {
                val lowTirMgdl = Constants.STATS_RANGE_LOW_MMOL * Constants.MMOLL_TO_MGDL
                val highTirMgdl = Constants.STATS_RANGE_HIGH_MMOL * Constants.MMOLL_TO_MGDL
                val lowTitMgdl = Constants.STATS_TARGET_LOW_MMOL * Constants.MMOLL_TO_MGDL
                val highTitMgdl = Constants.STATS_TARGET_HIGH_MMOL * Constants.MMOLL_TO_MGDL

                val tir7 = tirCalculator.calculate(7, lowTirMgdl, highTirMgdl)
                val tir30 = tirCalculator.calculate(30, lowTirMgdl, highTirMgdl)
                val tit7 = tirCalculator.calculate(7, lowTitMgdl, highTitMgdl)
                val tit30 = tirCalculator.calculate(30, lowTitMgdl, highTitMgdl)

                TirStatsData(
                    tir7 = tir7,
                    averageTir7 = tirCalculator.averageTIR(tir7),
                    averageTir30 = tirCalculator.averageTIR(tir30),
                    lowTirMgdl = lowTirMgdl,
                    highTirMgdl = highTirMgdl,
                    lowTitMgdl = lowTitMgdl,
                    highTitMgdl = highTitMgdl,
                    averageTit7 = tirCalculator.averageTIR(tit7),
                    averageTit30 = tirCalculator.averageTIR(tit30)
                )
            }
            uiState.update { it.copy(tirStatsData = data, tirLoading = false) }
        }
    }

    private fun loadDexcomTirStats() {
        viewModelScope.launch {
            uiState.update { it.copy(dexcomTirLoading = true) }
            val data = withContext(Dispatchers.IO) {
                dexcomTirCalculator.calculate()
            }
            uiState.update { it.copy(dexcomTirData = data, dexcomTirLoading = false) }
        }
    }

    private fun loadActivityStats() {
        viewModelScope.launch {
            uiState.update { it.copy(activityLoading = true) }
            val data = withContext(Dispatchers.IO) {
                activityMonitor.getActivityStats()
            }
            uiState.update { it.copy(activityStatsData = data, activityLoading = false) }
        }
    }

    fun toggleTddExpanded() {
        uiState.update { it.copy(tddExpanded = !it.tddExpanded) }
    }

    fun toggleTirExpanded() {
        uiState.update { it.copy(tirExpanded = !it.tirExpanded) }
    }

    fun toggleDexcomTirExpanded() {
        uiState.update { it.copy(dexcomTirExpanded = !it.dexcomTirExpanded) }
    }

    fun toggleActivityExpanded() {
        uiState.update { it.copy(activityExpanded = !it.activityExpanded) }
    }

    fun toggleTddCycleExpanded() {
        val wasExpanded = uiState.value.tddCycleExpanded
        if (!wasExpanded && cachedDailyTdds.isEmpty()) {
            // Atomic: set expanded + loading in single update so AnimatedVisibility sees loading immediately
            uiState.update { it.copy(tddCycleExpanded = true, tddCycleLoading = true, tddCycleProgress = 0f) }
            loadCyclePatternData()
        } else {
            uiState.update { it.copy(tddCycleExpanded = !wasExpanded) }
        }
    }

    fun updateCycleOffset(offset: Int) {
        preferences.put(IntNonKey.TddCycleOffset, offset)
        uiState.update { it.copy(tddCycleOffset = offset) }
        // Recompute cycles from cached data (no DB refetch)
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                computeCycleData(cachedDailyTdds, offset)
            }
            uiState.update { it.copy(tddCyclePatternData = data) }
        }
    }

    private fun loadCyclePatternData() {
        cycleLoadJob?.cancel()
        cycleLoadJob = viewModelScope.launch {
            val tddList = mutableListOf<TDD>()
            val now = dateUtil.now()
            val dayMs = 24 * 3600 * 1000L

            // Phase 1: Load recent 56 days in 7-day chunks (8 chunks) — show graph ASAP
            val phase1Chunks = 8
            val totalChunks = phase1Chunks + 4 // 8 + 4 = 12 total
            for (chunk in 0 until phase1Chunks) {
                val chunkTdds = withContext(Dispatchers.IO) {
                    val timestamp = now - chunk.toLong() * 7 * dayMs
                    tddCalculator.calculate(timestamp, 7, allowMissingDays = true)
                }
                addUniqueTdds(tddList, chunkTdds)
                uiState.update { it.copy(tddCycleProgress = (chunk + 1).toFloat() / totalChunks) }
            }
            // Show graph after phase 1 (56 days = 2 cycles minimum)
            updateCycleGraph(tddList)

            // Phase 2: Load older data in 28-day chunks (4 × 28 = 112 more days, total 168)
            for (chunk in 0 until 4) {
                val chunkTdds = withContext(Dispatchers.IO) {
                    val timestamp = now - (56 + chunk.toLong() * 28) * dayMs
                    tddCalculator.calculate(timestamp, 28, allowMissingDays = true)
                }
                addUniqueTdds(tddList, chunkTdds)
                uiState.update { it.copy(tddCycleProgress = (phase1Chunks + chunk + 1).toFloat() / totalChunks) }
                updateCycleGraph(tddList)
            }
            uiState.update { it.copy(tddCycleLoading = false) }
        }
    }

    private fun addUniqueTdds(target: MutableList<TDD>, source: LongSparseArray<TDD>?) {
        source ?: return
        for (i in 0 until source.size()) {
            val tdd = source.valueAt(i)
            if (target.none { it.timestamp == tdd.timestamp }) {
                target.add(tdd)
            }
        }
    }

    private fun updateCycleGraph(tddList: MutableList<TDD>) {
        tddList.sortByDescending { it.timestamp }
        cachedDailyTdds = tddList.toList()
        val data = computeCycleData(cachedDailyTdds, uiState.value.tddCycleOffset)
        uiState.update { it.copy(tddCyclePatternData = data) }
    }

    private fun computeCycleData(dailyTdds: List<TDD>, offset: Int): TddCyclePatternData? {
        if (dailyTdds.size < 56) return null // Need at least 2 cycles

        // Skip offset days from newest, then chunk into 28-day cycles
        val usable = dailyTdds.drop(offset)
        val cycleCount = usable.size / 28
        if (cycleCount < 2) return null

        val rawCycles = mutableListOf<CycleSeries>()
        val cleanedCycles = mutableListOf<CycleSeries>()

        for (c in 0 until cycleCount) {
            val start = c * 28
            val chunk = usable.subList(start, start + 28).reversed() // oldest first → day 1..28

            val rawValues = chunk.map { it.totalAmount }
            // Cleaned TDD = TDD minus insulin attributed to carbs (carbs/IC computed at carb time)
            val cleanedValues = chunk.map { tdd ->
                (tdd.totalAmount - tdd.carbInsulin).coerceAtLeast(0.0)
            }

            val startDate = dateUtil.dateString(chunk.first().timestamp)
            rawCycles.add(CycleSeries(dayValues = rawValues, cycleIndex = c, startDate = startDate))
            cleanedCycles.add(CycleSeries(dayValues = cleanedValues, cycleIndex = c, startDate = startDate))
        }

        // Compute per-day averages (28 points)
        val rawAverage = (0 until 28).map { day ->
            rawCycles.map { it.dayValues[day] }.average()
        }
        val cleanedAverage = (0 until 28).map { day ->
            cleanedCycles.map { it.dayValues[day] }.average()
        }

        return TddCyclePatternData(
            rawCycles = rawCycles,
            cleanedCycles = cleanedCycles,
            rawAverage = rawAverage,
            cleanedAverage = cleanedAverage,
            cycleCount = cycleCount,
            totalDaysAvailable = dailyTdds.size
        )
    }

    fun showRecalculateDialog() {
        uiState.update { it.copy(showRecalculateDialog = true) }
    }

    fun dismissRecalculateDialog() {
        uiState.update { it.copy(showRecalculateDialog = false) }
    }

    fun confirmRecalculateTdd() {
        uiState.update { it.copy(showRecalculateDialog = false) }
        uel.log(Action.STAT_RESET, Sources.Stats)
        viewModelScope.launch {
            persistenceLayer.clearCachedTddData(0)
            loadTddStats()
        }
    }

    fun showResetActivityDialog() {
        uiState.update { it.copy(showResetActivityDialog = true) }
    }

    fun dismissResetActivityDialog() {
        uiState.update { it.copy(showResetActivityDialog = false) }
    }

    fun confirmResetActivityStats() {
        uiState.update { it.copy(showResetActivityDialog = false) }
        uel.log(Action.STAT_RESET, Sources.Stats)
        viewModelScope.launch(Dispatchers.IO) {
            activityMonitor.reset()
            loadActivityStats()
        }
    }
}

/**
 * UI state for StatsScreen
 */
@Immutable
data class StatsUiState(
    val tddStatsData: TddStatsData? = null,
    val tirStatsData: TirStatsData? = null,
    val dexcomTirData: DexcomTIR? = null,
    val activityStatsData: List<ActivityStats>? = null,
    val tddCyclePatternData: TddCyclePatternData? = null,
    val tddLoading: Boolean = true,
    val tirLoading: Boolean = true,
    val dexcomTirLoading: Boolean = true,
    val activityLoading: Boolean = true,
    val tddCycleLoading: Boolean = true,
    val tddCycleProgress: Float = 0f,
    val tddExpanded: Boolean = true,
    val tirExpanded: Boolean = false,
    val dexcomTirExpanded: Boolean = false,
    val activityExpanded: Boolean = false,
    val tddCycleExpanded: Boolean = false,
    val tddCycleOffset: Int = 0,
    val showRecalculateDialog: Boolean = false,
    val showResetActivityDialog: Boolean = false
)
