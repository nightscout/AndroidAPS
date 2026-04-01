package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow

/**
 * Domain models for calculated graph data.
 * These are view-agnostic - no GraphView or Vico dependencies.
 * Populated by workers, consumed by ViewModels/UI.
 */

/**
 * BG range classification for coloring
 */
enum class BgRange {

    HIGH,      // Above high mark
    IN_RANGE,  // Within target range
    LOW        // Below low mark
}

/**
 * BG data point type for different rendering styles and colors
 */
enum class BgType {

    REGULAR,         // Regular CGM reading (outlined circle, white)
    BUCKETED,        // 5-min bucketed average (filled circle, colored by range)
    IOB_PREDICTION,  // IOB-based prediction (filled concentric circles, blue)
    COB_PREDICTION,  // COB-based prediction (filled concentric circles, orange)
    A_COB_PREDICTION,// Absorbed COB prediction (filled concentric circles, lighter orange)
    UAM_PREDICTION,  // Unannounced meals prediction (filled concentric circles, yellow)
    ZT_PREDICTION    // Zero-temp prediction (filled concentric circles, cyan)
}

/**
 * Time range for graph display
 */
data class TimeRange(
    val fromTime: Long,
    val toTime: Long,
    val endTime: Long // includes predictions
)

/**
 * Individual BG data point
 */
data class BgDataPoint(
    val timestamp: Long,
    val value: Double,             // Already converted to user's units (mg/dL or mmol/L)
    val range: BgRange,            // Range classification (high/in-range/low)
    val type: BgType,              // Type determines rendering style and color
    val filledGap: Boolean = false, // For bucketed data - if true, render semi-transparent
)

// ============================================================================
// BG Info Display Data (Overview info section)
// ============================================================================

/**
 * Current BG info for the overview info section display.
 * Contains the latest BG value and related status for UI display.
 * Color mapping (BgRange -> theme color) happens in UI layer.
 */
data class BgInfoData(
    val bgValue: Double,           // Value in user's units (mg/dL or mmol/L)
    val bgText: String,            // Formatted BG string (e.g., "120" or "6.7")
    val bgRange: BgRange,          // HIGH/IN_RANGE/LOW - for color mapping in UI
    val isOutdated: Boolean,       // True if timestamp > 9 min ago (for strikethrough)
    val timestamp: Long,           // BG timestamp (for timeAgo calculation)
    val trendArrow: TrendArrow?,   // Trend direction arrow
    val trendDescription: String,  // Accessibility description of trend
    val delta: Double?,            // Delta in user units (signed)
    val deltaText: String?,        // Formatted delta string (e.g., "+5" or "-0.3")
    val shortAvgDelta: Double?,    // Short average delta in user units
    val shortAvgDeltaText: String?,// Formatted short avg delta
    val longAvgDelta: Double?,     // Long average delta in user units
    val longAvgDeltaText: String?, // Formatted long avg delta
)

/**
 * Generic data point for line graphs (IOB, COB, Activity, BGI, Ratio, etc.)
 */
data class GraphDataPoint(
    val timestamp: Long,
    val value: Double
)

/**
 * Deviation type for color classification in deviation bars
 */
enum class DeviationType {

    POSITIVE,    // Green - above expected (pastSensitivity = "+")
    NEGATIVE,    // Red - below expected (pastSensitivity = "-")
    EQUAL,       // Black/gray - as expected (pastSensitivity = "=")
    UAM,         // UAM color (type = "uam")
    CSF          // Gray - carb absorption (type = "csf" or pastSensitivity = "C")
}

/**
 * Deviation bar data point with color classification
 */
data class DeviationDataPoint(
    val timestamp: Long,
    val value: Double,
    val deviationType: DeviationType
)

/**
 * COB failover marker point (when min absorption rate is used)
 */
data class CobFailOverPoint(
    val timestamp: Long,
    val cobValue: Double
)

// ============================================================================
// Graph-level data classes (one per secondary graph)
// ============================================================================

/**
 * IOB graph data: regular IOB line + prediction points
 */
data class IobGraphData(
    val iob: List<GraphDataPoint>,
    val predictions: List<GraphDataPoint>
)

/**
 * Absolute IOB graph data: absolute IOB line only
 */
data class AbsIobGraphData(
    val absIob: List<GraphDataPoint>
)

/**
 * COB graph data: COB line + failover marker points
 */
data class CobGraphData(
    val cob: List<GraphDataPoint>,
    val failOverPoints: List<CobFailOverPoint>
)

/**
 * Activity graph data: historical activity + prediction line
 */
data class ActivityGraphData(
    val activity: List<GraphDataPoint>,
    val activityPrediction: List<GraphDataPoint>
)

/**
 * BGI (Blood Glucose Impact) graph data: historical + prediction line
 */
data class BgiGraphData(
    val bgi: List<GraphDataPoint>,
    val bgiPrediction: List<GraphDataPoint>
)

/**
 * Deviations graph data: deviation bars with color types
 */
data class DeviationsGraphData(
    val deviations: List<DeviationDataPoint>
)

/**
 * Autosens ratio graph data: ratio percentage line
 */
data class RatioGraphData(
    val ratio: List<GraphDataPoint>
)

/**
 * Deviation slope graph data: max and min slope lines
 */
data class DevSlopeGraphData(
    val dsMax: List<GraphDataPoint>,
    val dsMin: List<GraphDataPoint>
)

/**
 * Variable sensitivity graph data: sensitivity line from APS results
 */
data class VarSensGraphData(
    val varSens: List<GraphDataPoint>
)

/**
 * Basal graph data for BG graph overlay (dual Y-axis).
 * Profile basal: dashed step line (scheduled profile rate).
 * Actual basal: solid step line with area fill (profile rate when no temp, temp absolute when temp active).
 * maxBasal used for Y-axis scaling: maxY = maxBasal * 4.0 so basal occupies ~25% of chart height.
 */
data class BasalGraphData(
    val profileBasal: List<GraphDataPoint>,
    val actualBasal: List<GraphDataPoint>,
    val maxBasal: Double
)

/**
 * Target line data for BG graph overlay.
 * Step-function showing target midpoint: TT midpoint when active, profile target midpoint otherwise.
 * Rendered as a step line on the BG (start) Y-axis.
 */
data class TargetLineData(
    val targets: List<GraphDataPoint>  // step-function transition points (timestamp, value in user units)
)

// ============================================================================
// Treatment / Therapy Graph Data (main graph overlays)
// ============================================================================

/**
 * Bolus type for rendering (different shapes/colors)
 */
enum class BolusType {
    NORMAL,   // Regular bolus (triangle shape)
    SMB       // Super micro bolus (small diamond)
}

/**
 * Individual bolus data point for the main graph
 */
data class BolusGraphPoint(
    val timestamp: Long,
    val amount: Double,        // Insulin amount in units
    val bolusType: BolusType,  // NORMAL or SMB
    val isValid: Boolean,
    val label: String          // Pre-formatted amount string (pump-supported step)
)

/**
 * Individual carbs data point for the main graph
 */
data class CarbsGraphPoint(
    val timestamp: Long,
    val amount: Double,        // Carbs amount in grams
    val isValid: Boolean,
    val label: String          // Pre-formatted carbs string (e.g., "45 g")
)

/**
 * Extended bolus data point for the main graph
 */
data class ExtendedBolusGraphPoint(
    val timestamp: Long,
    val amount: Double,        // Total insulin amount
    val rate: Double,          // Rate per hour
    val duration: Long,        // Duration in ms
    val label: String          // Pre-formatted string (e.g., "1.5U/h 2.0U")
)

/**
 * Therapy event type for rendering (different shapes/colors)
 */
enum class TherapyEventType {
    MBG,                   // Manual blood glucose
    FINGER_STICK,          // Finger stick BG check
    ANNOUNCEMENT,          // Announcement
    SETTINGS_EXPORT,       // Settings export
    EXERCISE,              // Exercise
    GENERAL,               // General event (no duration)
    GENERAL_WITH_DURATION  // General event with duration
}

/**
 * Therapy event data point for the main graph
 */
data class TherapyEventGraphPoint(
    val timestamp: Long,
    val eventType: TherapyEventType,
    val label: String,         // Note or translated type name
    val duration: Long         // Duration in ms (0 if no duration)
)

/**
 * Effective profile switch data point for the main graph
 */
data class EpsGraphPoint(
    val timestamp: Long,
    val originalPercentage: Int,   // Profile percentage (100 = normal)
    val originalTimeshift: Long,   // Timeshift in ms
    val profileName: String,       // originalCustomizedName for tap display
    val label: String              // Short label (e.g., "110%" or "110%,-2h")
)

/**
 * Container for all treatment graph data (overlaid on main BG graph).
 * Populated reactively by OverviewDataCacheImpl observing DB changes.
 */
data class TreatmentGraphData(
    val boluses: List<BolusGraphPoint>,
    val carbs: List<CarbsGraphPoint>,
    val extendedBoluses: List<ExtendedBolusGraphPoint>,
    val therapyEvents: List<TherapyEventGraphPoint>
)

// ============================================================================
// Overview Display State (TempTarget, Profile chips)
// ============================================================================

/**
 * Temp target chip state classification
 */
enum class TempTargetState {

    /** No temp target, showing profile default */
    NONE,

    /** Temp target is active */
    ACTIVE,

    /** No temp target, but APS adjusted the target */
    ADJUSTED
}

/**
 * Temp target display data for overview chips.
 * Stores raw data - ViewModel computes display text with remaining time.
 */
data class TempTargetDisplayData(
    val targetRangeText: String,         // Formatted target range only (e.g., "100-120")
    val state: TempTargetState,          // NONE/ACTIVE/ADJUSTED for UI styling
    val timestamp: Long,                 // When TT started (for progress calculation)
    val duration: Long,                  // TT duration in ms (0 if not active)
    val reason: TT.Reason? = null         // TT reason for icon coloring (null if no TT)
)

/**
 * Profile display data for overview chips.
 * Stores raw data - ViewModel computes display text with remaining time.
 */
data class ProfileDisplayData(
    val profileName: String,             // Profile name only (no remaining time)
    val isLoaded: Boolean,               // True if profile is loaded
    val isModified: Boolean,             // True if percentage/timeshift/duration modified
    val timestamp: Long,                 // When profile switch started (for progress)
    val duration: Long                   // Profile switch duration in ms (0 if permanent)
)

/**
 * Running mode display data for overview chips.
 * Stores raw data - ViewModel computes display text with remaining time.
 */
data class RunningModeDisplayData(
    val mode: RM.Mode,                   // Current running mode
    val timestamp: Long,                 // When mode started (for progress calculation)
    val duration: Long                   // Mode duration in ms (0 if permanent)
)

// ============================================================================
// Treatment Belt Graph Data (running mode segments for belt graph overlay)
// ============================================================================

/**
 * A single running mode time segment for the treatment belt graph.
 */
data class RunningModeSegment(
    val mode: RM.Mode,
    val startTime: Long,
    val endTime: Long
)

/**
 * Running mode graph data: time-ordered segments for belt background coloring.
 * Each segment represents a contiguous period where the loop was in a specific mode.
 * CLOSED_LOOP and RESUME segments are transparent (no colored rectangle drawn).
 */
data class RunningModeGraphData(
    val segments: List<RunningModeSegment>
)