package app.aaps.ui.compose.overview

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusData
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.interfaces.overview.graph.AbsIobGraphData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BasalGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.BolusGraphPoint
import app.aaps.core.interfaces.overview.graph.BolusType
import app.aaps.core.interfaces.overview.graph.CarbsGraphPoint
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.EpsGraphPoint
import app.aaps.core.interfaces.overview.graph.ExtendedBolusGraphPoint
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.HeartRateGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.RunningModeDisplayData
import app.aaps.core.interfaces.overview.graph.RunningModeGraphData
import app.aaps.core.interfaces.overview.graph.RunningModeSegment
import app.aaps.core.interfaces.overview.graph.StepsGraphData
import app.aaps.core.interfaces.overview.graph.TargetLineData
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.overview.graph.TherapyEventGraphPoint
import app.aaps.core.interfaces.overview.graph.TherapyEventType
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromGv
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of OverviewDataCache using MutableStateFlow.
 * Singleton cache that observes database changes and updates UI state reactively.
 *
 * Architecture: Reactive Data Observation
 * - Observes GlucoseValue, TempTarget, EffectiveProfileSwitch changes via Flow
 * - Updates state flows immediately when data changes
 * - No dependency on calculation workflow for basic display data
 * - Each data type has its own StateFlow for granular recomposition
 *
 * MIGRATION NOTE: This coexists with OverviewDataImpl during migration.
 * Workers populate graph data. After migration complete, OverviewDataImpl will be deleted.
 */
@OptIn(FlowPreview::class)
@Singleton
class OverviewDataCacheImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val trendCalculator: TrendCalculator,
    private val iobCobCalculator: IobCobCalculator,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val loop: Loop,
    private val config: Config,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val nsSettingsStatus: NSSettingsStatus,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val decimalFormatter: DecimalFormatter,
    private val translator: Translator,
    private val rh: ResourceHelper
) : OverviewDataCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // =========================================================================
    // State flows (must be declared before init block to avoid race conditions)
    // =========================================================================

    private val _calcProgressFlow = MutableStateFlow(100)
    override val calcProgressFlow: StateFlow<Int> = _calcProgressFlow.asStateFlow()

    // Time range
    private val _timeRangeFlow = MutableStateFlow<TimeRange?>(null)
    override val timeRangeFlow: StateFlow<TimeRange?> = _timeRangeFlow.asStateFlow()

    // BG data flows
    private val _bgReadingsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val bgReadingsFlow: StateFlow<List<BgDataPoint>> = _bgReadingsFlow.asStateFlow()
    private val _bucketedDataFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val bucketedDataFlow: StateFlow<List<BgDataPoint>> = _bucketedDataFlow.asStateFlow()
    private val _predictionsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    override val predictionsFlow: StateFlow<List<BgDataPoint>> = _predictionsFlow.asStateFlow()
    private val _bgInfoFlow = MutableStateFlow<BgInfoData?>(null)
    override val bgInfoFlow: StateFlow<BgInfoData?> = _bgInfoFlow.asStateFlow()

    // Overview chip flows
    private val _tempTargetFlow = MutableStateFlow<TempTargetDisplayData?>(null)
    override val tempTargetFlow: StateFlow<TempTargetDisplayData?> = _tempTargetFlow.asStateFlow()
    private val _profileFlow = MutableStateFlow<ProfileDisplayData?>(null)
    override val profileFlow: StateFlow<ProfileDisplayData?> = _profileFlow.asStateFlow()
    private val _runningModeFlow = MutableStateFlow<RunningModeDisplayData?>(null)
    override val runningModeFlow: StateFlow<RunningModeDisplayData?> = _runningModeFlow.asStateFlow()

    override fun refreshTempTarget() {
        scope.launch { updateTempTargetFromDatabase() }
    }

    // Secondary graph flows
    private val _iobGraphFlow = MutableStateFlow(IobGraphData(emptyList(), emptyList()))
    override val iobGraphFlow: StateFlow<IobGraphData> = _iobGraphFlow.asStateFlow()
    private val _absIobGraphFlow = MutableStateFlow(AbsIobGraphData(emptyList()))
    override val absIobGraphFlow: StateFlow<AbsIobGraphData> = _absIobGraphFlow.asStateFlow()
    private val _cobGraphFlow = MutableStateFlow(CobGraphData(emptyList(), emptyList()))
    override val cobGraphFlow: StateFlow<CobGraphData> = _cobGraphFlow.asStateFlow()
    private val _activityGraphFlow = MutableStateFlow(ActivityGraphData(emptyList(), emptyList()))
    override val activityGraphFlow: StateFlow<ActivityGraphData> = _activityGraphFlow.asStateFlow()
    private val _bgiGraphFlow = MutableStateFlow(BgiGraphData(emptyList(), emptyList()))
    override val bgiGraphFlow: StateFlow<BgiGraphData> = _bgiGraphFlow.asStateFlow()
    private val _deviationsGraphFlow = MutableStateFlow(DeviationsGraphData(emptyList()))
    override val deviationsGraphFlow: StateFlow<DeviationsGraphData> = _deviationsGraphFlow.asStateFlow()
    private val _ratioGraphFlow = MutableStateFlow(RatioGraphData(emptyList()))
    override val ratioGraphFlow: StateFlow<RatioGraphData> = _ratioGraphFlow.asStateFlow()
    private val _devSlopeGraphFlow = MutableStateFlow(DevSlopeGraphData(emptyList(), emptyList()))
    override val devSlopeGraphFlow: StateFlow<DevSlopeGraphData> = _devSlopeGraphFlow.asStateFlow()
    private val _varSensGraphFlow = MutableStateFlow(VarSensGraphData(emptyList()))
    override val varSensGraphFlow: StateFlow<VarSensGraphData> = _varSensGraphFlow.asStateFlow()
    private val _heartRateGraphFlow = MutableStateFlow(HeartRateGraphData(emptyList()))
    override val heartRateGraphFlow: StateFlow<HeartRateGraphData> = _heartRateGraphFlow.asStateFlow()
    private val _stepsGraphFlow = MutableStateFlow(StepsGraphData(emptyList()))
    override val stepsGraphFlow: StateFlow<StepsGraphData> = _stepsGraphFlow.asStateFlow()
    private val _treatmentGraphFlow = MutableStateFlow(TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList()))
    override val treatmentGraphFlow: StateFlow<TreatmentGraphData> = _treatmentGraphFlow.asStateFlow()
    private val _epsGraphFlow = MutableStateFlow<List<EpsGraphPoint>>(emptyList())
    override val epsGraphFlow: StateFlow<List<EpsGraphPoint>> = _epsGraphFlow.asStateFlow()
    private val _basalGraphFlow = MutableStateFlow(BasalGraphData(emptyList(), emptyList(), 0.0))
    override val basalGraphFlow: StateFlow<BasalGraphData> = _basalGraphFlow.asStateFlow()
    private val _targetLineFlow = MutableStateFlow(TargetLineData(emptyList()))
    override val targetLineFlow: StateFlow<TargetLineData> = _targetLineFlow.asStateFlow()
    private val _runningModeGraphFlow = MutableStateFlow(RunningModeGraphData(emptyList()))
    override val runningModeGraphFlow: StateFlow<RunningModeGraphData> = _runningModeGraphFlow.asStateFlow()

    // NSClient status
    private val _nsClientStatusFlow = MutableStateFlow(AapsClientStatusData())
    override val nsClientStatusFlow: StateFlow<AapsClientStatusData> = _nsClientStatusFlow.asStateFlow()

    init {
        // Load initial data from database
        scope.launch {
            aapsLogger.debug(LTag.UI, "OverviewDataCache: Loading initial data")
            updateBgInfoFromDatabase()
            updateProfileFromDatabase()
            updateTempTargetFromDatabase()
            updateRunningModeFromDatabase()
        }

        // Observe GlucoseValue changes
        scope.launch {
            persistenceLayer.observeChanges(GV::class.java).collect { glucoseValues ->
                aapsLogger.debug(LTag.UI, "GV change detected, updating BgInfo (${glucoseValues.size} values)")
                updateBgInfoFromDatabase()
            }
        }

        // TT and EPS chip observers are handled below in Category B reactive graph observers
        // RM chip observer is also handled below in Category B

        // Refresh trend arrow after bucketed data is created (bucketed data is ready after this event)
        scope.launch {
            rxBus.toFlow(EventBucketedDataCreated::class.java).collect {
                aapsLogger.debug(LTag.UI, "Bucketed data created, refreshing BgInfo for trend arrow")
                updateBgInfoFromDatabase()
            }
        }

        // Observe calculation progress from workers
        scope.launch {
            rxBus.toFlow(EventIobCalculationProgress::class.java).collect {
                _calcProgressFlow.value = it.finalPercent
            }
        }

        // Observe unit changes — affects BG value formatting and TT target range text
        scope.launch {
            preferences.observe(StringKey.GeneralUnits).collect {
                aapsLogger.debug(LTag.UI, "Units changed, refreshing BgInfo and TempTarget")
                updateBgInfoFromDatabase()
                updateTempTargetFromDatabase()
            }
        }

        // Observe high/low mark changes — affects BG range classification (circle color)
        scope.launch {
            preferences.observe(UnitDoubleKey.OverviewHighMark).collect {
                aapsLogger.debug(LTag.UI, "High mark changed, refreshing BgInfo")
                updateBgInfoFromDatabase()
            }
        }
        scope.launch {
            preferences.observe(UnitDoubleKey.OverviewLowMark).collect {
                aapsLogger.debug(LTag.UI, "Low mark changed, refreshing BgInfo")
                updateBgInfoFromDatabase()
            }
        }

        // =========================================================================
        // Category B reactive graph observers (treatments, RM, TT, basal)
        // =========================================================================

        // Observe treatment-related DB changes
        for (type in listOf(
            BS::class.java, CA::class.java, EB::class.java, TE::class.java
        )) {
            scope.launch {
                persistenceLayer.observeChanges(type)
                    .debounce(300)
                    .collect { rebuildTreatmentGraph() }
            }
        }
        // Observe HR changes for treatment graph + heart rate graph
        scope.launch {
            persistenceLayer.observeChanges(HR::class.java)
                .debounce(300)
                .collect {
                    rebuildTreatmentGraph()
                    rebuildHeartRateGraph()
                }
        }
        // Observe SC changes for treatment graph + steps graph
        scope.launch {
            persistenceLayer.observeChanges(SC::class.java)
                .debounce(300)
                .collect {
                    rebuildTreatmentGraph()
                    rebuildStepsGraph()
                }
        }
        // Rebuild all Category B graphs when time range changes
        scope.launch {
            timeRangeFlow
                .filterNotNull()
                .debounce(300)
                .collect {
                    rebuildTreatmentGraph()
                    rebuildEpsGraph()
                    rebuildRunningModeGraph()
                    rebuildTargetLine()
                    rebuildBasalGraph()
                    rebuildHeartRateGraph()
                    rebuildStepsGraph()
                }
        }

        // Observe running mode changes for graph + chip
        scope.launch {
            persistenceLayer.observeChanges(RM::class.java)
                .debounce(300)
                .collect {
                    updateRunningModeFromDatabase()
                    rebuildRunningModeGraph()
                }
        }

        // Observe TT changes for target line graph + chip
        scope.launch {
            persistenceLayer.observeChanges(TT::class.java)
                .debounce(300)
                .collect {
                    updateTempTargetFromDatabase()
                    rebuildTargetLine()
                }
        }
        // Refresh TT chip after APS loop runs so the APS-adjusted target (read from
        // loop.lastRun.constraintsProcessed.targetBG) is reflected in the ADJUSTED state.
        scope.launch {
            merge(
                rxBus.toFlow(EventLoopUpdateGui::class.java),
                rxBus.toFlow(EventNewOpenLoopNotification::class.java)
            ).collect { updateTempTargetFromDatabase() }
        }
        // EPS changes affect EPS graph, profile chip, TT chip, target line, and basal
        scope.launch {
            persistenceLayer.observeChanges(EPS::class.java)
                .debounce(300)
                .collect {
                    rebuildEpsGraph()
                    delay(500) // Allow ProfileFunctionImpl cache invalidation
                    updateProfileFromDatabase()
                    updateTempTargetFromDatabase()
                    rebuildTargetLine()
                    rebuildBasalGraph()
                }
        }

        // Observe basal-related DB changes
        scope.launch {
            persistenceLayer.observeChanges(TB::class.java)
                .debounce(300)
                .collect { rebuildBasalGraph() }
        }
        scope.launch {
            persistenceLayer.observeChanges(EB::class.java)
                .debounce(300)
                .collect { rebuildBasalGraph() }
        }

        // NSClient status: initial load + subscribe to updates + 60s ticker for time-ago refresh
        if (config.AAPSCLIENT) {
            scope.launch { rebuildNsClientStatus() }
            scope.launch {
                rxBus.toFlow(EventNsClientStatusUpdated::class.java).collect {
                    rebuildNsClientStatus()
                }
            }
            scope.launch {
                while (true) {
                    delay(60_000)
                    rebuildNsClientStatus()
                }
            }
        }
    }

    // =========================================================================
    // BgInfo computation
    // =========================================================================

    private suspend fun updateBgInfoFromDatabase() {
        // Use bucketed (smoothed) data like legacy, with raw DB fallback
        val lastBg = iobCobCalculator.ads.bucketedData?.firstOrNull()
        val lastGv = lastBg ?: persistenceLayer.getLastGlucoseValue()?.let { InMemoryGlucoseValue.fromGv(it) }
        if (lastGv == null) {
            _bgInfoFlow.value = null
            return
        }

        val bgMgdl = lastGv.recalculated
        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        val valueInUnits = profileUtil.fromMgdlToUnits(bgMgdl)

        val bgRange = when {
            valueInUnits > highMark -> BgRange.HIGH
            valueInUnits < lowMark  -> BgRange.LOW
            else                    -> BgRange.IN_RANGE
        }

        val isOutdated = lastGv.timestamp < dateUtil.now() - 9 * 60 * 1000L
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val trendDescription = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData

        _bgInfoFlow.value = BgInfoData(
            bgValue = valueInUnits,
            bgText = profileUtil.fromMgdlToStringInUnits(bgMgdl),
            bgRange = bgRange,
            isOutdated = isOutdated,
            timestamp = lastGv.timestamp,
            trendArrow = trendArrow,
            trendDescription = trendDescription,
            delta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.delta) },
            deltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.delta) },
            shortAvgDelta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.shortAvgDelta) },
            shortAvgDeltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.shortAvgDelta) },
            longAvgDelta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.longAvgDelta) },
            longAvgDeltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.longAvgDelta) }
        )
    }

    // =========================================================================
    // TempTarget computation
    // =========================================================================

    private suspend fun updateTempTargetFromDatabase() {
        val units = profileFunction.getUnits()
        val now = dateUtil.now()
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(now)

        val displayData = if (tempTarget != null) {
            // Active TT - store target range only (ViewModel adds "until HH:MM")
            val targetRange = profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units)
            TempTargetDisplayData(
                targetRangeText = targetRange,
                state = TempTargetState.ACTIVE,
                timestamp = tempTarget.timestamp,
                duration = tempTarget.duration,
                reason = tempTarget.reason
            )
        } else {
            // No active TT - check profile
            val profile = profileFunction.getProfile()
            if (profile != null) {
                // Check if APS/AAPSCLIENT has adjusted target
                val targetUsed = when {
                    config.APS        -> loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
                    config.AAPSCLIENT -> processedDeviceStatusData.getAPSResult()?.targetBG ?: 0.0
                    else              -> 0.0
                }

                if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                    // APS adjusted target
                    val apsTarget = profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units)
                    TempTargetDisplayData(apsTarget, TempTargetState.ADJUSTED, 0L, 0L)
                } else {
                    // Default profile target
                    val profileTarget = profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
                    TempTargetDisplayData(profileTarget, TempTargetState.NONE, 0L, 0L)
                }
            } else {
                // No profile loaded yet
                TempTargetDisplayData("", TempTargetState.NONE, 0L, 0L)
            }
        }

        _tempTargetFlow.value = displayData
    }

    // =========================================================================
    // Profile computation
    // =========================================================================

    private suspend fun updateProfileFromDatabase() {
        val profile = profileFunction.getProfile()
        var isModified = false
        var timestamp = 0L
        var duration = 0L

        if (profile is ProfileSealed.EPS) {
            val eps = profile.value
            isModified = eps.originalPercentage != 100 || eps.originalTimeshift != 0L || eps.originalDuration != 0L
            timestamp = eps.timestamp
            duration = eps.originalDuration
        }

        _profileFlow.value = ProfileDisplayData(
            profileName = profileFunction.getProfileName(),  // Raw name, ViewModel adds remaining time
            isLoaded = profile != null,
            isModified = isModified,
            timestamp = timestamp,
            duration = duration
        )
    }

    // =========================================================================
    // Running mode computation
    // =========================================================================

    private fun updateRunningModeFromDatabase() {
        val mode = loop.runningMode
        val rmRecord = loop.runningModeRecord

        // Store raw data only - ViewModel computes display text
        _runningModeFlow.value = RunningModeDisplayData(
            mode = mode,
            timestamp = rmRecord.timestamp,
            duration = rmRecord.duration
        )
    }

    // =========================================================================
    // Update methods
    // =========================================================================

    override fun updateTimeRange(range: TimeRange?) {
        _timeRangeFlow.value = range
    }

    override fun updateBgReadings(data: List<BgDataPoint>) {
        _bgReadingsFlow.value = data
    }

    override fun updateBucketedData(data: List<BgDataPoint>) {
        _bucketedDataFlow.value = data
    }

    override fun updatePredictions(data: List<BgDataPoint>) {
        _predictionsFlow.value = data
    }

    override fun updateBgInfo(data: BgInfoData?) {
        _bgInfoFlow.value = data
    }

    override fun updateIobGraph(data: IobGraphData) {
        _iobGraphFlow.value = data
    }

    override fun updateAbsIobGraph(data: AbsIobGraphData) {
        _absIobGraphFlow.value = data
    }

    override fun updateCobGraph(data: CobGraphData) {
        _cobGraphFlow.value = data
    }

    override fun updateActivityGraph(data: ActivityGraphData) {
        _activityGraphFlow.value = data
    }

    override fun updateBgiGraph(data: BgiGraphData) {
        _bgiGraphFlow.value = data
    }

    override fun updateDeviationsGraph(data: DeviationsGraphData) {
        _deviationsGraphFlow.value = data
    }

    override fun updateRatioGraph(data: RatioGraphData) {
        _ratioGraphFlow.value = data
    }

    override fun updateDevSlopeGraph(data: DevSlopeGraphData) {
        _devSlopeGraphFlow.value = data
    }

    override fun updateVarSensGraph(data: VarSensGraphData) {
        _varSensGraphFlow.value = data
    }

    override fun updateHeartRateGraph(data: HeartRateGraphData) {
        _heartRateGraphFlow.value = data
    }

    override fun updateStepsGraph(data: StepsGraphData) {
        _stepsGraphFlow.value = data
    }

    // =========================================================================
    // Category B: Reactive graph builders (treatments, RM, TT, basal)
    // =========================================================================

    /** Compute graph time range from current timeRangeFlow */
    private fun graphTimeRange(): Pair<Long, Long>? {
        val range = timeRangeFlow.value ?: return null
        val toTime = range.endTime
        val fromTime = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
        return fromTime to toTime
    }

    private suspend fun rebuildTreatmentGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        // Boluses and SMBs
        val bolusPoints = persistenceLayer.getBolusesFromTimeToTime(fromTime, toTime, true)
            .filter { it.type == BS.Type.NORMAL || it.type == BS.Type.SMB }
            .map { bs ->
                BolusGraphPoint(
                    timestamp = bs.timestamp,
                    amount = bs.amount,
                    bolusType = if (bs.type == BS.Type.SMB) BolusType.SMB else BolusType.NORMAL,
                    isValid = bs.isValid,
                    label = decimalFormatter.toPumpSupportedBolus(bs.amount, bolusStep)
                )
            }

        // Carbs
        val carbsPoints = persistenceLayer.getCarbsFromTimeToTimeExpanded(fromTime, toTime, true)
            .map { ca ->
                CarbsGraphPoint(
                    timestamp = ca.timestamp,
                    amount = ca.amount,
                    isValid = ca.isValid && ca.amount > 0,
                    label = rh.gs(R.string.format_carbs, ca.amount.toInt())
                )
            }

        // Extended boluses
        val extendedBolusPoints = if (!activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            persistenceLayer.getExtendedBolusesStartingFromTimeToTime(fromTime, toTime, true)
                .filter { it.duration != 0L }
                .map { eb ->
                    ExtendedBolusGraphPoint(
                        timestamp = eb.timestamp,
                        amount = eb.amount,
                        rate = eb.rate,
                        duration = eb.duration,
                        label = rh.gs(R.string.extended_bolus_data_point_graph, eb.amount, eb.rate)
                    )
                }
        } else emptyList()

        // Therapy events
        val therapyEventPoints = persistenceLayer.getTherapyEventDataFromToTime(fromTime - T.hours(6).msecs(), toTime)
            .filter { te -> te.timestamp + te.duration >= fromTime && te.timestamp <= toTime }
            .map { te ->
                val teType = when {
                    te.type == TE.Type.NS_MBG                -> TherapyEventType.MBG
                    te.type == TE.Type.FINGER_STICK_BG_VALUE -> TherapyEventType.FINGER_STICK
                    te.type == TE.Type.ANNOUNCEMENT          -> TherapyEventType.ANNOUNCEMENT
                    te.type == TE.Type.SETTINGS_EXPORT       -> TherapyEventType.SETTINGS_EXPORT
                    te.type == TE.Type.EXERCISE              -> TherapyEventType.EXERCISE
                    te.duration > 0                          -> TherapyEventType.GENERAL_WITH_DURATION
                    else                                     -> TherapyEventType.GENERAL
                }
                val teLabel = if (!te.note.isNullOrBlank()) te.note!! else translator.translate(te.type)
                TherapyEventGraphPoint(
                    timestamp = te.timestamp,
                    eventType = teType,
                    label = teLabel,
                    duration = te.duration
                )
            }

        _treatmentGraphFlow.value = TreatmentGraphData(
            boluses = bolusPoints,
            carbs = carbsPoints,
            extendedBoluses = extendedBolusPoints,
            therapyEvents = therapyEventPoints
        )
    }

    private suspend fun rebuildEpsGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        _epsGraphFlow.value = persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(fromTime, toTime, true)
            .map { eps ->
                val label = buildString {
                    if (eps.originalPercentage != 100) append("${eps.originalPercentage}%")
                    if (eps.originalPercentage != 100 && eps.originalTimeshift != 0L) append(",")
                    if (eps.originalTimeshift != 0L) append("${T.msecs(eps.originalTimeshift).hours()}${rh.gs(app.aaps.core.interfaces.R.string.shorthour)}")
                }
                EpsGraphPoint(
                    timestamp = eps.timestamp,
                    originalPercentage = eps.originalPercentage,
                    originalTimeshift = eps.originalTimeshift,
                    profileName = eps.originalCustomizedName,
                    label = label
                )
            }
    }

    private suspend fun rebuildRunningModeGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        var endTime = toTime
        loop.lastRun?.constraintsProcessed?.let { endTime = max(it.latestPredictionsTime, endTime) }

        // Batch query all RM records in range (instead of per-slot getRunningModeActiveAt)
        val rmRecords = persistenceLayer.getRunningModesFromTimeToTime(fromTime, endTime, true)

        // Get mode active at fromTime for the initial segment
        val initialMode = persistenceLayer.getRunningModeActiveAt(fromTime)

        // Build segments from sorted records
        val segments = mutableListOf<RunningModeSegment>()
        var currentMode = initialMode.mode
        var currentRecordEnd = initialMode.timestamp + initialMode.duration
        var segmentStart = fromTime

        for (rm in rmRecords) {
            if (rm.timestamp > segmentStart && rm.mode != currentMode) {
                segments.add(RunningModeSegment(currentMode, segmentStart, rm.timestamp))
                currentMode = rm.mode
                currentRecordEnd = rm.timestamp + rm.duration
                segmentStart = rm.timestamp
            }
        }
        // Final segment capped by record's planned end time
        segments.add(RunningModeSegment(currentMode, segmentStart, min(currentRecordEnd, endTime)))

        _runningModeGraphFlow.value = RunningModeGraphData(segments = segments)
    }

    private suspend fun rebuildTargetLine() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        val profile = profileFunction.getProfile() ?: return
        var endTime = toTime
        loop.lastRun?.constraintsProcessed?.let { endTime = max(it.latestPredictionsTime, endTime) }

        val targets = mutableListOf<GraphDataPoint>()
        var lastTarget = -1.0
        var time = fromTime
        while (time < endTime) {
            val tt = persistenceLayer.getTemporaryTargetActiveAt(time)
            val value = if (tt != null) {
                profileUtil.fromMgdlToUnits(tt.target())
            } else {
                profileUtil.fromMgdlToUnits((profile.getTargetLowMgdl(time) + profile.getTargetHighMgdl(time)) / 2)
            }
            if (value != lastTarget) {
                targets.add(GraphDataPoint(time, value))
                lastTarget = value
            }
            time += 5 * 60 * 1000L
        }
        // Final point
        if (lastTarget >= 0.0) targets.add(GraphDataPoint(endTime, lastTarget))

        _targetLineFlow.value = TargetLineData(targets)
    }

    private suspend fun rebuildBasalGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        val profileBasal = mutableListOf<GraphDataPoint>()
        val actualBasal = mutableListOf<GraphDataPoint>()
        var lastProfileBasal = -1.0
        var lastActualBasal = -1.0
        var maxBasal = 0.0

        var time = fromTime
        while (time < toTime) {
            val profile = profileFunction.getProfile(time)
            if (profile == null) {
                time += 60 * 1000L
                continue
            }
            val basalData = iobCobCalculator.getBasalData(profile, time)
            val profileBasalValue = basalData.basal
            val actualBasalValue = if (basalData.isTempBasalRunning) basalData.tempBasalAbsolute else profileBasalValue

            if (profileBasalValue != lastProfileBasal) {
                profileBasal.add(GraphDataPoint(time, profileBasalValue))
                lastProfileBasal = profileBasalValue
            }
            if (actualBasalValue != lastActualBasal) {
                actualBasal.add(GraphDataPoint(time, actualBasalValue))
                lastActualBasal = actualBasalValue
            }
            maxBasal = max(maxBasal, max(profileBasalValue, actualBasalValue))

            time += 60 * 1000L
        }

        // Final points
        if (lastProfileBasal >= 0.0) profileBasal.add(GraphDataPoint(toTime, lastProfileBasal))
        if (lastActualBasal >= 0.0) actualBasal.add(GraphDataPoint(toTime, lastActualBasal))

        _basalGraphFlow.value = BasalGraphData(profileBasal, actualBasal, maxBasal)
    }

    // =========================================================================
    // NSClient status rebuild
    // =========================================================================

    private fun rebuildNsClientStatus() {
        val now = dateUtil.now()
        val pumpItem = processedDeviceStatusData.pumpData?.let { pumpData ->
            val level = when {
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("urgentClock") * 60 * 1000L < now                               -> AapsClientLevel.URGENT
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("urgentRes")                                                -> AapsClientLevel.URGENT
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("urgentBattP")                          -> AapsClientLevel.URGENT
                !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("urgentBattV") -> AapsClientLevel.URGENT
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("warnClock") * 60 * 1000L < now                                 -> AapsClientLevel.WARN
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("warnRes")                                                  -> AapsClientLevel.WARN
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("warnBattP")                            -> AapsClientLevel.WARN
                !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("warnBattV")   -> AapsClientLevel.WARN
                else                                                                                                                   -> AapsClientLevel.INFO
            }
            // Format: "75% 3 min ago" (running mode excluded — already shown in RunningMode chip)
            val value = buildString {
                if (pumpData.isPercent) append("${pumpData.percent}% ")
                if (!pumpData.isPercent && pumpData.voltage > 0) append("${Round.roundTo(pumpData.voltage, 0.001)} ")
                append(dateUtil.minAgo(rh, pumpData.clock))
            }.trim()
            val dialogText = buildString {
                pumpData.extended?.let {
                    append(it.replace("<br>", "\n").replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim())
                }
            }
            AapsClientStatusItem(
                label = rh.gs(R.string.pump),
                value = value,
                level = level,
                dialogTitle = rh.gs(R.string.pump),
                dialogText = dialogText
            )
        }

        val openApsItem = if (processedDeviceStatusData.openAPSData.clockSuggested != 0L) {
            val clockSuggested = processedDeviceStatusData.openAPSData.clockSuggested
            val level = when {
                clockSuggested + T.mins(preferences.get(IntKey.NsClientUrgentAlarmStaleData).toLong()).msecs() < now -> AapsClientLevel.URGENT
                clockSuggested + T.mins(preferences.get(IntKey.NsClientAlarmStaleData).toLong()).msecs() < now       -> AapsClientLevel.WARN
                else                                                                                                 -> AapsClientLevel.INFO
            }
            // Match original format: "2 min ago"
            val value = dateUtil.minOrSecAgo(rh, clockSuggested)
            val dialogText = buildString {
                processedDeviceStatusData.openAPSData.enacted?.let {
                    if (processedDeviceStatusData.openAPSData.clockEnacted != clockSuggested) {
                        append("Enacted: ${dateUtil.minAgo(rh, processedDeviceStatusData.openAPSData.clockEnacted)}")
                        append(" ${it.reason}")
                        append("\n")
                    }
                }
                processedDeviceStatusData.openAPSData.suggested?.let {
                    append("Suggested: ${dateUtil.minAgo(rh, clockSuggested)}")
                    append(" ${it.reason}")
                }
            }
            AapsClientStatusItem(
                label = rh.gs(R.string.openaps_short),
                value = value,
                level = level,
                dialogTitle = rh.gs(R.string.openaps),
                dialogText = dialogText
            )
        } else null

        val uploaderItem = if (processedDeviceStatusData.uploaderMap.isNotEmpty()) {
            var minBattery = 100
            var isCharging = false
            for ((_, uploader) in processedDeviceStatusData.uploaderMap) {
                if (uploader.battery <= minBattery) {
                    minBattery = uploader.battery
                    isCharging = uploader.isCharging == true
                }
            }
            // Match original format: "ᴪ 93%" or "93%"
            val value = buildString {
                if (isCharging) append("\u26A1 ")
                append("$minBattery%")
            }
            val dialogText = buildString {
                for ((device, uploader) in processedDeviceStatusData.uploaderMap) {
                    append("$device: ${uploader.battery}%")
                    if (uploader.isCharging == true) append(" \u26A1")
                    append("\n")
                }
            }.trimEnd()
            AapsClientStatusItem(
                label = rh.gs(R.string.uploader_short),
                value = value,
                level = AapsClientLevel.INFO,
                dialogTitle = rh.gs(R.string.uploader),
                dialogText = dialogText
            )
        } else null

        _nsClientStatusFlow.value = AapsClientStatusData(pump = pumpItem, openAps = openApsItem, uploader = uploaderItem)
    }

    private suspend fun rebuildHeartRateGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        val heartRates = persistenceLayer.getHeartRatesFromTimeToTime(fromTime, toTime)
            .filter { it.isValid }
            // Plot at sampling start: HR.timestamp is the end of the sampling period (matches legacy HeartRateDataPoint.getX())
            .map { hr -> GraphDataPoint(timestamp = hr.timestamp - hr.duration, value = hr.beatsPerMinute) }
        _heartRateGraphFlow.value = HeartRateGraphData(heartRates)
    }

    private suspend fun rebuildStepsGraph() {
        val (fromTime, toTime) = graphTimeRange() ?: return
        val steps = persistenceLayer.getStepsCountFromTimeToTime(fromTime, toTime)
            .filter { it.isValid }
            .map { sc -> GraphDataPoint(timestamp = sc.timestamp, value = sc.steps5min.toDouble()) }
        _stepsGraphFlow.value = StepsGraphData(steps)
    }

    override fun reset() {
        _timeRangeFlow.value = null
        _bgReadingsFlow.value = emptyList()
        _bucketedDataFlow.value = emptyList()
        _predictionsFlow.value = emptyList()
        _bgInfoFlow.value = null
        _tempTargetFlow.value = null
        _profileFlow.value = null
        _runningModeFlow.value = null
        // Secondary graph flows
        _iobGraphFlow.value = IobGraphData(emptyList(), emptyList())
        _absIobGraphFlow.value = AbsIobGraphData(emptyList())
        _cobGraphFlow.value = CobGraphData(emptyList(), emptyList())
        _activityGraphFlow.value = ActivityGraphData(emptyList(), emptyList())
        _bgiGraphFlow.value = BgiGraphData(emptyList(), emptyList())
        _deviationsGraphFlow.value = DeviationsGraphData(emptyList())
        _ratioGraphFlow.value = RatioGraphData(emptyList())
        _devSlopeGraphFlow.value = DevSlopeGraphData(emptyList(), emptyList())
        _varSensGraphFlow.value = VarSensGraphData(emptyList())
        _heartRateGraphFlow.value = HeartRateGraphData(emptyList())
        _stepsGraphFlow.value = StepsGraphData(emptyList())
        _treatmentGraphFlow.value = TreatmentGraphData(emptyList(), emptyList(), emptyList(), emptyList())
        _epsGraphFlow.value = emptyList()
        _basalGraphFlow.value = BasalGraphData(emptyList(), emptyList(), 0.0)
        _targetLineFlow.value = TargetLineData(emptyList())
        _runningModeGraphFlow.value = RunningModeGraphData(emptyList())
        _nsClientStatusFlow.value = AapsClientStatusData()
        _calcProgressFlow.value = 100
    }
}