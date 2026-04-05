package app.aaps.core.interfaces.overview.graph

import kotlinx.coroutines.flow.StateFlow

/**
 * Cache for overview data. Populated reactively by observing database changes, observed by ViewModels via StateFlow.
 * This replaces OverviewDataImpl for the new Compose system.
 *
 * Architecture: Reactive Data Observation
 * - Observes database changes (GV, TT, EPS) via Flow
 * - Updates state flows immediately when data changes
 * - No dependency on calculation workflow for basic display data
 * - Each data type has its own StateFlow for granular updates
 *
 * MIGRATION NOTE: During migration, workers may still populate some graph data.
 * After migration complete, OverviewDataImpl will be deleted.
 */
interface OverviewDataCache {

    // =========================================================================
    // Time range
    // =========================================================================
    val timeRangeFlow: StateFlow<TimeRange?>
    fun updateTimeRange(range: TimeRange?)

    // Calculation progress (0-100)
    val calcProgressFlow: StateFlow<Int>

    // =========================================================================
    // BG data flows
    // =========================================================================
    val bgReadingsFlow: StateFlow<List<BgDataPoint>>
    val bucketedDataFlow: StateFlow<List<BgDataPoint>>
    val predictionsFlow: StateFlow<List<BgDataPoint>>
    val bgInfoFlow: StateFlow<BgInfoData?>

    fun updateBgReadings(data: List<BgDataPoint>)
    fun updateBucketedData(data: List<BgDataPoint>)
    fun updatePredictions(data: List<BgDataPoint>)
    fun updateBgInfo(data: BgInfoData?)

    // =========================================================================
    // Overview chip display data (reactive to database changes)
    // =========================================================================
    val tempTargetFlow: StateFlow<TempTargetDisplayData?>
    val profileFlow: StateFlow<ProfileDisplayData?>
    val runningModeFlow: StateFlow<RunningModeDisplayData?>
    fun refreshTempTarget()

    // =========================================================================
    // Secondary graph flows (Phase 5) - one per graph
    // Non-nullable with empty data default (consistent with BG flows)
    // =========================================================================

    // IOB graph: regular IOB line + prediction points
    val iobGraphFlow: StateFlow<IobGraphData>
    fun updateIobGraph(data: IobGraphData)

    // Absolute IOB graph: absolute IOB line only
    val absIobGraphFlow: StateFlow<AbsIobGraphData>
    fun updateAbsIobGraph(data: AbsIobGraphData)

    // COB graph: COB line + failover marker points
    val cobGraphFlow: StateFlow<CobGraphData>
    fun updateCobGraph(data: CobGraphData)

    // Activity graph: historical activity + prediction line
    val activityGraphFlow: StateFlow<ActivityGraphData>
    fun updateActivityGraph(data: ActivityGraphData)

    // BGI graph: historical + prediction line
    val bgiGraphFlow: StateFlow<BgiGraphData>
    fun updateBgiGraph(data: BgiGraphData)

    // Deviations graph: deviation bars with color types
    val deviationsGraphFlow: StateFlow<DeviationsGraphData>
    fun updateDeviationsGraph(data: DeviationsGraphData)

    // Ratio graph: autosens ratio percentage line
    val ratioGraphFlow: StateFlow<RatioGraphData>
    fun updateRatioGraph(data: RatioGraphData)

    // Dev slope graph: max and min slope lines
    val devSlopeGraphFlow: StateFlow<DevSlopeGraphData>
    fun updateDevSlopeGraph(data: DevSlopeGraphData)

    // Variable sensitivity graph: sensitivity line from APS results
    val varSensGraphFlow: StateFlow<VarSensGraphData>
    fun updateVarSensGraph(data: VarSensGraphData)

    // Heart rate graph: BPM readings from smartwatch
    val heartRateGraphFlow: StateFlow<HeartRateGraphData>
    fun updateHeartRateGraph(data: HeartRateGraphData)

    // Steps count graph: 5-minute step counts from smartwatch
    val stepsGraphFlow: StateFlow<StepsGraphData>
    fun updateStepsGraph(data: StepsGraphData)

    // =========================================================================
    // Treatment graph flows (main graph overlays)
    // Populated reactively by observing database changes — no worker needed.
    // =========================================================================

    // Treatments: boluses, SMBs, carbs, extended boluses, therapy events
    val treatmentGraphFlow: StateFlow<TreatmentGraphData>

    // Effective profile switches (separate graph from treatments)
    val epsGraphFlow: StateFlow<List<EpsGraphPoint>>

    // Basal graph: profile basal (dashed) + actual delivered basal (solid + fill)
    val basalGraphFlow: StateFlow<BasalGraphData>

    // Target line: step-function showing target midpoint (TT or profile default)
    val targetLineFlow: StateFlow<TargetLineData>

    // Running mode graph: time segments for treatment belt background coloring
    val runningModeGraphFlow: StateFlow<RunningModeGraphData>

    // =========================================================================
    // NSClient status (pump/openAPS/uploader from Nightscout) — only for AAPSCLIENT builds
    // =========================================================================
    val nsClientStatusFlow: StateFlow<AapsClientStatusData>

    fun reset()
}
