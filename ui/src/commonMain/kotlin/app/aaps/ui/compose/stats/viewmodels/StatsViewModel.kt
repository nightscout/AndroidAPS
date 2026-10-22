package app.aaps.ui.compose.stats.viewmodels

import androidx.collection.LongSparseArray
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TDD
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.stats.DexcomTIR
import app.aaps.core.interfaces.stats.DexcomTirCalculator
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.stats.AgpStatsData
import app.aaps.ui.compose.stats.AgpStatsRange
import app.aaps.ui.compose.stats.CycleSeries
import app.aaps.ui.compose.stats.TddCyclePatternData
import app.aaps.ui.compose.stats.TddStatsData
import app.aaps.ui.compose.stats.TirStatsData
import app.aaps.ui.compose.stats.calculateAgpStatsData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How long the totals wait after a database change before recomputing.
 *
 * Five seconds, matching `IobCobCalculatorPlugin`'s own history debounce. A Nightscout sync writes
 * treatments in many chunks and each one is a change, so a short window here would start a seven day
 * recalculation over and over while the sync is still running.
 */
private const val TDD_RELOAD_DEBOUNCE_MS = 5_000L

/**
 * ViewModel for StatsScreen managing statistics data loading and state.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@OptIn(FlowPreview::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class StatsViewModel(
    private val tddCalculator: TddCalculator,
    private val tirCalculator: TirCalculator,
    private val dexcomTirCalculator: DexcomTirCalculator,
    private val persistenceLayer: PersistenceLayer,
    val rh: TextResolver,
    private val uel: UserEntryLogger,
    val dateUtil: DateUtil,
    val profileUtil: ProfileUtil,
    private val preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var cycleLoadJob: Job? = null
    private var agpLoadJob: Job? = null

    /**
     * Held so a reload can replace the one before it instead of racing it.
     *
     * `calculate(7)` walks seven days in five minute steps, so two of them running at once is both
     * wasteful and a coin toss over which `_uiState` update lands last.
     */
    private var tddLoadJob: Job? = null

    init {
        val savedOffset = preferences.get(IntNonKey.TddCycleOffset)
        val tddExpanded = preferences.get(BooleanNonKey.StatsTddExpanded)
        val tirExpanded = preferences.get(BooleanNonKey.StatsTirExpanded)
        val dexcomTirExpanded = preferences.get(BooleanNonKey.StatsDexcomTirExpanded)
        val tddCycleExpanded = preferences.get(BooleanNonKey.StatsTddCycleExpanded)
        _uiState.update {
            it.copy(
                tddCycleOffset = savedOffset,
                tddExpanded = tddExpanded,
                tirExpanded = tirExpanded,
                dexcomTirExpanded = dexcomTirExpanded,
                tddCycleExpanded = tddCycleExpanded
            )
        }
        loadAllStats()
        observeTddInputs()
        // Cycle pattern data is loaded lazily; if the section was left expanded, load it now
        if (tddCycleExpanded) {
            _uiState.update { it.copy(tddCycleLoading = true, tddCycleProgress = 0f) }
            loadCyclePatternData()
        }
    }

    /**
     * Reload the totals when the data they are made of changes.
     *
     * Without this the screen read the numbers once, on open, and never again - so a sync running
     * underneath, or the loop rebuilding the cache, left whatever was on screen at the moment the
     * screen opened. "Recalculate" was the only way to see current figures, and it clears the cache
     * as well, which made a stale reading indistinguishable from a wrong one. The graphs never had
     * this problem because they are driven by StateFlow and repaint on every database change.
     *
     * The same five types `IobCobCalculatorPlugin` invalidates its own caches on, because they are
     * exactly the ones a total daily dose is computed from: boluses and extended boluses, carbs,
     * temporary basals, and the effective profile switch that sets the basal rate underneath them.
     *
     * Debounced hard, and not for cosmetic reasons: a full sync writes in many chunks, and each one
     * would otherwise start a seven day recalculation in five minute steps.
     */
    private fun observeTddInputs() {
        merge(
            persistenceLayer.observeChanges(BS::class).map {},
            persistenceLayer.observeChanges(CA::class).map {},
            persistenceLayer.observeChanges(TB::class).map {},
            persistenceLayer.observeChanges(EB::class).map {},
            persistenceLayer.observeChanges(EPS::class).map {}
        )
            .debounce(TDD_RELOAD_DEBOUNCE_MS)
            .onEach { loadTddStats() }
            .launchIn(viewModelScope)
    }

    fun loadAllStats() {
        loadTddStats()
        loadAgpStats(uiState.value.agpRange)
        loadTirStats()
        loadDexcomTirStats()
    }

    fun loadAgpStats(range: AgpStatsRange) {
        agpLoadJob?.cancel()
        _uiState.update { it.copy(agpRange = range, agpLoading = true, agpError = false) }
        agpLoadJob = viewModelScope.launch {
            try {
                val data = withContext(aapsIoDispatcher) {
                    val endTime = dateUtil.now()
                    val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(range.startTime(endTime), endTime, true)
                    calculateAgpStatsData(
                        readings = readings,
                        lowMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.OverviewLowMark)),
                        highMgdl = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.OverviewHighMark))
                    )
                }
                _uiState.update { it.copy(agpStatsData = data, agpLoading = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update { it.copy(agpLoading = false, agpError = true) }
            }
        }
    }

    fun toggleAgpExpanded() {
        _uiState.update { it.copy(agpExpanded = !it.agpExpanded) }
    }

    private fun loadTddStats() {
        tddLoadJob?.cancel()
        tddLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(tddLoading = true) }
            val data = withContext(aapsIoDispatcher) {
                val tdds = tddCalculator.calculate(7, allowMissingDays = true)
                val averageTdd = tddCalculator.averageTDD(tdds)
                val todayTdd = tddCalculator.calculateToday()
                TddStatsData(tdds = tdds, averageTdd = averageTdd, todayTdd = todayTdd)
            }
            _uiState.update { it.copy(tddStatsData = data, tddLoading = false) }
        }
    }

    private fun loadTirStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(tirLoading = true) }
            val data = withContext(aapsIoDispatcher) {
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
            _uiState.update { it.copy(tirStatsData = data, tirLoading = false) }
        }
    }

    private fun loadDexcomTirStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(dexcomTirLoading = true) }
            val data = withContext(aapsIoDispatcher) {
                dexcomTirCalculator.calculate()
            }
            _uiState.update { it.copy(dexcomTirData = data, dexcomTirLoading = false) }
        }
    }

    fun toggleTddExpanded() {
        val expanded = !uiState.value.tddExpanded
        preferences.put(BooleanNonKey.StatsTddExpanded, expanded)
        _uiState.update { it.copy(tddExpanded = expanded) }
    }

    fun toggleTirExpanded() {
        val expanded = !uiState.value.tirExpanded
        preferences.put(BooleanNonKey.StatsTirExpanded, expanded)
        _uiState.update { it.copy(tirExpanded = expanded) }
    }

    fun toggleDexcomTirExpanded() {
        val expanded = !uiState.value.dexcomTirExpanded
        preferences.put(BooleanNonKey.StatsDexcomTirExpanded, expanded)
        _uiState.update { it.copy(dexcomTirExpanded = expanded) }
    }

    fun toggleTddCycleExpanded() {
        val wasExpanded = uiState.value.tddCycleExpanded
        preferences.put(BooleanNonKey.StatsTddCycleExpanded, !wasExpanded)
        if (!wasExpanded && uiState.value.tddCycleEntries.isEmpty()) {
            // Atomic: set expanded + loading in single update so AnimatedVisibility sees loading immediately
            _uiState.update { it.copy(tddCycleExpanded = true, tddCycleLoading = true, tddCycleProgress = 0f) }
            loadCyclePatternData()
        } else {
            _uiState.update { it.copy(tddCycleExpanded = !wasExpanded) }
        }
    }

    fun updateCycleOffset(offset: Int) {
        preferences.put(IntNonKey.TddCycleOffset, offset)
        _uiState.update { it.copy(tddCycleOffset = offset) }
        // Recompute cycles from cached data (no DB refetch)
        val entries = uiState.value.tddCycleEntries
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                computeCycleData(entries, offset)
            }
            _uiState.update { it.copy(tddCyclePatternData = data) }
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
                val chunkTdds = withContext(aapsIoDispatcher) {
                    val timestamp = now - chunk.toLong() * 7 * dayMs
                    tddCalculator.calculate(timestamp, 7, allowMissingDays = true)
                }
                addUniqueTdds(tddList, chunkTdds)
                _uiState.update { it.copy(tddCycleProgress = (chunk + 1).toFloat() / totalChunks) }
            }
            // Show graph after phase 1 (56 days = 2 cycles minimum)
            updateCycleGraph(tddList)

            // Phase 2: Load older data in 28-day chunks (4 × 28 = 112 more days, total 168)
            for (chunk in 0 until 4) {
                val chunkTdds = withContext(aapsIoDispatcher) {
                    val timestamp = now - (56 + chunk.toLong() * 28) * dayMs
                    tddCalculator.calculate(timestamp, 28, allowMissingDays = true)
                }
                addUniqueTdds(tddList, chunkTdds)
                _uiState.update { it.copy(tddCycleProgress = (phase1Chunks + chunk + 1).toFloat() / totalChunks) }
                updateCycleGraph(tddList)
            }
            _uiState.update { it.copy(tddCycleLoading = false) }
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
        val entries = tddList.toList()
        val data = computeCycleData(entries, uiState.value.tddCycleOffset)
        _uiState.update { it.copy(tddCycleEntries = entries, tddCyclePatternData = data) }
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
        _uiState.update { it.copy(showRecalculateDialog = true) }
    }

    fun dismissRecalculateDialog() {
        _uiState.update { it.copy(showRecalculateDialog = false) }
    }

    fun confirmRecalculateTdd() {
        _uiState.update { it.copy(showRecalculateDialog = false) }
        uel.log(Action.STAT_RESET, Sources.Stats)
        viewModelScope.launch {
            persistenceLayer.clearCachedTddData(0)
            loadTddStats()
        }
    }
}

/**
 * UI state for StatsScreen
 */
@Immutable
data class StatsUiState(
    val tddStatsData: TddStatsData? = null,
    val agpStatsData: AgpStatsData? = null,
    val agpRange: AgpStatsRange = AgpStatsRange.TODAY,
    val agpLoading: Boolean = true,
    val agpError: Boolean = false,
    val agpExpanded: Boolean = true,
    val tirStatsData: TirStatsData? = null,
    val dexcomTirData: DexcomTIR? = null,
    val tddCycleEntries: List<TDD> = emptyList(),
    val tddCyclePatternData: TddCyclePatternData? = null,
    val tddLoading: Boolean = true,
    val tirLoading: Boolean = true,
    val dexcomTirLoading: Boolean = true,
    val tddCycleLoading: Boolean = true,
    val tddCycleProgress: Float = 0f,
    val tddExpanded: Boolean = true,
    val tirExpanded: Boolean = true,
    val dexcomTirExpanded: Boolean = true,
    val tddCycleExpanded: Boolean = false,
    val tddCycleOffset: Int = 0,
    val showRecalculateDialog: Boolean = false
)
