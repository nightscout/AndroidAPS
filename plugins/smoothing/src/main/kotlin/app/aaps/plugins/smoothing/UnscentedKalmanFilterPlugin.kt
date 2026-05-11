package app.aaps.plugins.smoothing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.smoothing.SmoothingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Adaptive Unscented Kalman Filter with RTS smoothing.
 *
 * KEY FEATURES:
 * - FIXED Q (process noise) tuned for realistic meal/insulin responses.
 * - ADAPTIVE R (measurement noise) adapting to changing sensor quality.
 * - Learned R parameter persists across function calls and app restarts.
 * - Chi-squared based outlier detection (99.99% confidence).
 * - Automatically resets learning on actual sensor changes (via EventTherapyEventChange).
 * - Outlier threshold scales with current uncertainty (P + R).
 * - Event-based reset (not time-based guessing).
 *
 * State vector: x = [G, Ġ]^T
 *   - G: glucose concentration (mg/dL)
 *   - Ġ: rate of glucose change (mg/dL/min)
 *
 * Process model: x_{t+1} = f(x_t) + w_t
 *   - f(x_t) = [G + Ġ·Δt, Ġ·damping]^T
 *   - w_t ~ N(0, Q) where Q is fixed based on realistic physiology.
 *
 * Measurement model: z_t = h(x_t) + v_t
 *   - h(x_t) = G
 *   - v_t ~ N(0, R) where R is adaptive based on sensor quality.
 */
@Singleton
class UnscentedKalmanFilterPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val persistenceLayer: PersistenceLayer,
    private val sp: SP
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .icon(Icons.Default.Timeline)
        .pluginName(R.string.UKF_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_UKF),
    aapsLogger,
    rh
), Smoothing {

    // ============================================================
    // UKF CONFIGURATION
    // ============================================================

    // State dimension.
    private val n = 2

    // UKF parameters (Merwe's scaled formulation).
    private val alpha = 0.1
    private val beta = 2.0
    private val kappa = 0.0

    // Derived parameters.
    private val lambda = alpha * alpha * (n + kappa) - n
    private val gamma = sqrt(n + lambda)

    // Sigma point weights.
    private val Wm = DoubleArray(2 * n + 1)
    private val Wc = DoubleArray(2 * n + 1)

    // FIXED process noise covariances - tuned for realistic glucose dynamics.
    // These values must accommodate meal responses (rapid rises) and insulin action (rapid falls).
    // Increased significantly from original to handle real-world glucose variability.
    private val Q = doubleArrayOf(
        1.0, 0.0,     // Glucose process noise
        0.0, 0.35     // Rate process noise
    )

    // Initial measurement noise (conservative starting point).
    private val R_INIT = 25.0  // ~5 mg/dL std dev - assumes moderate sensor quality.

    // Adaptive R bounds (variance, mg/dL^2).
    private val R_MIN = 16.0   // ~4 mg/dL std dev - excellent sensor.
    private val R_MAX = 225.0  // ~15 mg/dL std dev - poor sensor.
    private val R_EFF_MAX = 400.0

    // R adaptation window length for innovation statistics.
    private val innovationWindow = 18  // ≈90 minutes at 5‑min intervals.

    // Chi-squared based outlier detection (99.99% confidence, 1 DOF).
    private val CHI_SQUARED_THRESHOLD = 15.13  // Statistically rigorous.
    private val OUTLIER_ABSOLUTE = 65.0        // Absolute safety limit (mg/dL).

    // Covariance limits (tighter for faster recovery).
    private val MAX_GLUCOSE_VARIANCE = 400.0  // Max 20 mg/dL std dev.
    private val MAX_RATE_VARIANCE = 4.0       // Max 2 mg/dL/min std dev.

    // Innovation-based validation - detect parameter corruption.
    private val INNOVATION_RESET_THRESHOLD = 12.0   // Reset if avg innovation > 12.
    private val INNOVATION_VALIDATION_SAMPLES = 15  // Need 15 samples before validating.

    // Gap handling.
    private val MINOR_GAP_THRESHOLD = 7.0       // Minutes - bridge with prediction.
    private val MAJOR_GAP_THRESHOLD = 60.0      // Minutes - segment data.
    private val RATE_DECAY_TIME_CONSTANT = 30.0 // Minutes - physiological decay.

    // Hoisted constant for millis → minutes conversion to avoid repeated literal expressions.
    private val MILLIS_PER_MINUTE = 1000.0 * 60.0

    private fun rateDamp(dt: Double): Double = exp(-dt / RATE_DECAY_TIME_CONSTANT)

    // ============================================================
    // DATA STRUCTURES
    // ============================================================

    /**
     * Represents a continuous segment of glucose data without major gaps.
     *
     * @property startIdx index of the newest point in the segment (inclusive).
     * @property endIdx index of the oldest point in the segment (inclusive).
     */
    private data class DataSegment(
        val startIdx: Int,
        val endIdx: Int
    )

    /**
     * Internal data class for storing filter state during the forward pass.
     * Used by the RTS smoother to perform backward smoothing.
     *
     * @property x state estimate before update [glucose, rate].
     * @property P state covariance before update (2x2 in row-major).
     * @property xPred predicted state [glucose, rate].
     * @property PPred predicted covariance (2x2 in row-major).
     * @property dt time step used for this prediction (minutes).
     */
    private data class FilterState(

        val x: DoubleArray,
        val P: DoubleArray,
        val xPred: DoubleArray,
        val PPred: DoubleArray,
        val dt: Double
    )

    // ============================================================
    // PERSISTENT STATE
    // ============================================================

    // Learned measurement noise (variance).
    private var learnedR = R_INIT

    // Innovation tracking.
    // - innovations: normalized innovation squared ν² / (P[0] + R).
    // - rawInnovationVariance: raw innovation squared ν².
    // - predVarHistory: history of predicted variance P_pred[0].
    private val innovations = ArrayDeque<Double>(innovationWindow + 1)
    private val rawInnovationVariance = ArrayDeque<Double>(innovationWindow + 1)
    private val predVarHistory = ArrayDeque<Double>(innovationWindow + 1)

    // Session tracking.
    private var lastProcessedTimestamp: Long = 0
    private var lastSensorChangeTimestamp: Long = 0
    private var sensorSessionId: Int = 0
    private var sessionMeasurementCount: Long = 0
    private var sessionOutlierCount: Long = 0

    // Consecutive outlier counter (currently used only for diagnostics).
    private var consecutiveOutliers = 0

    // Event system.
    private val resetRequested = AtomicBoolean(false)
    private var scope: CoroutineScope? = null

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {
        // Initialize sigma point weights.
        Wm[0] = lambda / (n + lambda)
        Wc[0] = lambda / (n + lambda) + (1 - alpha * alpha + beta)
        val w = 1.0 / (2.0 * (n + lambda))
        for (i in 1 until 2 * n + 1) {
            Wm[i] = w
            Wc[i] = w
        }

        // Load persisted parameters.
        loadPersistedParameters()
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        // Subscribe to therapy events and load initial sensor state.
        persistenceLayer.observeChanges(TE::class.java)
            .onEach {
                checkForSensorChange()
            }
            .launchIn(newScope)

        newScope.launch {
            loadLastSensorChange()
        }
    }

    // ============================================================
    // PARAMETER PERSISTENCE
    // ============================================================

    /**
     * Load learned R parameter and session metadata from SharedPreferences.
     *
     * Q is never loaded; it is always the fixed physiological value defined in this class.
     */
    private fun loadPersistedParameters() {
        try {
            val lastSaved = sp.getLong("ukf_last_saved_timestamp", 0L)
            val savedSensorChange = sp.getLong("ukf_sensor_change_timestamp", 0L)

            if (lastSaved > 0) {
                lastSensorChangeTimestamp = savedSensorChange
                lastProcessedTimestamp = sp.getLong("ukf_last_processed_timestamp", 0L)
                learnedR = sp.getDouble("ukf_learned_r", R_INIT)
                sensorSessionId = sp.getInt("ukf_session_id", 0)

                // Validate loaded R.
                if (learnedR < R_MIN || learnedR > R_MAX) {
                    aapsLogger.info(
                        LTag.GLUCOSE,
                        "UKF: Loaded R ($learnedR) out of bounds, resetting to R_INIT"
                    )
                    learnedR = R_INIT
                }

                aapsLogger.info(
                    LTag.GLUCOSE,
                    "UKF: Loaded session $sensorSessionId " +
                        "(R=${String.format("%.1f", learnedR)}, " +
                        "Q_glucose=${String.format("%.2f", Q[0])} [FIXED], " +
                        "Q_rate=${String.format("%.4f", Q[3])} [FIXED])"
                )
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "UKF: Failed to load persisted parameters", e)
            // Reset to defaults on error.
            learnedR = R_INIT
        }
    }

    /**
     * Save learned R parameter and session metadata to SharedPreferences.
     *
     * Called whenever new data has been processed.
     * Q is never saved; it is always the fixed physiological value defined in this class.
     */
    private fun savePersistedParameters() {
        try {
            sp.putLong("ukf_last_saved_timestamp", System.currentTimeMillis())
            sp.putLong("ukf_sensor_change_timestamp", lastSensorChangeTimestamp)
            sp.putLong("ukf_last_processed_timestamp", lastProcessedTimestamp)
            sp.putDouble("ukf_learned_r", learnedR)
            sp.putInt("ukf_session_id", sensorSessionId)

            aapsLogger.debug(
                LTag.GLUCOSE,
                "UKF: Saved learned R for session $sensorSessionId"
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "UKF: Failed to save persisted parameters", e)
        }
    }

    // ============================================================
    // SENSOR CHANGE DETECTION
    // ============================================================

    /**
     * Load the most recent sensor change timestamp from the database.
     *
     * Called on plugin initialization and after therapy event changes.
     * Queries the last 30 days of therapy events for SENSOR_CHANGE entries.
     */
    private suspend fun loadLastSensorChange() {
        try {
            val therapyEvents = persistenceLayer.getTherapyEventDataFromTime(
                System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
                false
            )
            val latestSensorChange = therapyEvents
                .filter { it.type == TE.Type.SENSOR_CHANGE }
                .maxByOrNull { it.timestamp }

            latestSensorChange?.let { sensorChange ->
                if (sensorChange.timestamp > lastSensorChangeTimestamp) {
                    aapsLogger.info(
                        LTag.GLUCOSE,
                        "UKF: Detected sensor change at ${sensorChange.timestamp}"
                    )
                    lastSensorChangeTimestamp = sensorChange.timestamp

                    // If the sensor changed after the last processed reading, schedule a reset.
                    if (lastProcessedTimestamp > 0 &&
                        sensorChange.timestamp > lastProcessedTimestamp
                    ) {
                        aapsLogger.info(
                            LTag.GLUCOSE,
                            "UKF: Sensor changed after last processing, " +
                                "scheduling learning reset"
                        )
                        resetRequested.set(true)
                    }
                }
            }
        } catch (throwable: Throwable) {
            aapsLogger.error(
                LTag.GLUCOSE,
                "UKF: Error loading sensor change history",
                throwable
            )
        }
    }

    /**
     * Check for new sensor change events since the last known sensor change.
     *
     * Called when TE changes are observed to limit the query to
     * events after [lastSensorChangeTimestamp].
     */
    private fun checkForSensorChange() {
        scope?.launch {
            try {
                val therapyEvents = persistenceLayer.getTherapyEventDataFromTime(lastSensorChangeTimestamp, false)
                val newSensorChanges = therapyEvents.filter {
                    it.type == TE.Type.SENSOR_CHANGE &&
                        it.timestamp > lastSensorChangeTimestamp
                }

                if (newSensorChanges.isNotEmpty()) {
                    val latestChange = newSensorChanges.maxByOrNull { it.timestamp }!!
                    aapsLogger.info(
                        LTag.GLUCOSE,
                        "UKF: New sensor change at ${latestChange.timestamp}"
                    )
                    lastSensorChangeTimestamp = latestChange.timestamp
                    resetRequested.set(true)
                }
            } catch (throwable: Throwable) {
                aapsLogger.error(
                    LTag.GLUCOSE,
                    "UKF: Error checking for sensor changes",
                    throwable
                )
            }
        }
    }

    /**
     * Cleanup when the plugin stops.
     *
     * Called automatically by the plugin framework.
     */
    override fun onStop() {
        scope?.cancel()
        scope = null
        super.onStop()
    }

    // ============================================================
    // RESET LOGIC
    // ============================================================

    /**
     * Determine if learning should be reset.
     *
     * Uses actual sensor change events plus innovation-based validation.
     *
     * Reset conditions:
     * 1. Reset explicitly requested by the sensor change event listener.
     * 2. First ever call (lastProcessedTimestamp == 0).
     * 3. Timestamp corruption (time went backwards).
     * 4. Very large gaps (> 24 h) as a safety fallback for missed events.
     * 5. Severely corrupted R (detected via innovation statistics).
     *
     * @param currentTimestamp timestamp of the most recent glucose reading.
     * @return true if learning parameters should be reset to initial values.
     */
    private fun shouldResetLearning(currentTimestamp: Long): Boolean {
        if (resetRequested.getAndSet(false)) {
            aapsLogger.info(LTag.GLUCOSE, "UKF: Learning reset requested by sensor change event")
            return true
        }

        if (lastProcessedTimestamp == 0L) {
            aapsLogger.info(LTag.GLUCOSE, "UKF: First call, initializing learning")
            return true
        }

        val timeDiffMinutes = (currentTimestamp - lastProcessedTimestamp) / MILLIS_PER_MINUTE

        if (timeDiffMinutes < 0) {
            aapsLogger.info(LTag.GLUCOSE, "UKF: Timestamp went backwards, resetting learning")
            return true
        }

        if (timeDiffMinutes > 1440.0) {
            aapsLogger.info(
                LTag.GLUCOSE,
                "UKF: Very large gap (${timeDiffMinutes.toInt()} min), resetting"
            )
            return true
        }

        // Check for severely mis-tuned R based on innovation statistics.
        if (innovations.size >= INNOVATION_VALIDATION_SAMPLES) {
            val avgInnovation = innovations.average()
            if (avgInnovation > INNOVATION_RESET_THRESHOLD) {
                aapsLogger.info(
                    LTag.GLUCOSE,
                    "UKF: Severely mis-tuned parameters " +
                        "(avg innovation: ${String.format("%.1f", avgInnovation)}), " +
                        "resetting (R was ${String.format("%.1f", learnedR)})"
                )
                return true
            }
        }

        return false
    }

    /**
     * Reset learned R parameter and session statistics to initial values.
     *
     * Called when the sensor changes or significant data anomalies are detected.
     * Clears innovation history and increments session ID; Q remains fixed.
     */
    private fun resetLearning() {
        learnedR = R_INIT
        innovations.clear()
        rawInnovationVariance.clear()
        predVarHistory.clear()
        sensorSessionId++
        sessionMeasurementCount = 0
        sessionOutlierCount = 0
        consecutiveOutliers = 0

        aapsLogger.info(
            LTag.GLUCOSE,
            "UKF: Learning reset complete (session $sensorSessionId, " +
                "R=${String.format("%.1f", learnedR)}, " +
                "Q_glucose=${String.format("%.2f", Q[0])} [FIXED], " +
                "Q_rate=${String.format("%.4f", Q[3])} [FIXED])"
        )

        // Save the reset state.
        savePersistedParameters()
    }

    // ============================================================
    // MAIN FILTERING API
    // ============================================================

    @Suppress("LocalVariableName")
    override suspend fun smooth(
        data: MutableList<InMemoryGlucoseValue>,
        @Suppress("UNUSED_PARAMETER") context: SmoothingContext
    ): MutableList<InMemoryGlucoseValue> {
        if (data.isEmpty()) return data

        try {
            return smoothInternal(data)
        } catch (e: Exception) {
            aapsLogger.error(
                LTag.GLUCOSE,
                "UKF: Error during smoothing, falling back to raw values",
                e
            )
            copyRawToSmoothed(data)
            return data
        }
    }

    /**
     * Split data into segments at major gaps (>60 min).
     *
     * Each segment is filtered and smoothed independently to avoid spanning
     * long gaps or invalid regions.
     */
    private fun findDataSegments(data: List<InMemoryGlucoseValue>): List<DataSegment> {
        if (data.size < 2) return emptyList()

        val segments = mutableListOf<DataSegment>()
        var segmentStart = 0

        for (i in 0 until data.size - 1) {
            val timeDiff = (data[i].timestamp - data[i + 1].timestamp) / MILLIS_PER_MINUTE

            // Segment at major gaps (>60 min), invalid spacing, or error code.
            if (timeDiff > MAJOR_GAP_THRESHOLD || timeDiff < 2.0 || data[i].value == 38.0) {
                // Close current segment if it has enough points.
                if (i - segmentStart >= 2) {
                    segments.add(DataSegment(segmentStart, i))
                }
                // Next segment starts after the gap.
                segmentStart = i + 1
            }
        }

        // Add final segment.
        if (data.size - segmentStart >= 2) {
            segments.add(DataSegment(segmentStart, data.size - 1))
        }

        return segments
    }

    private fun smoothInternal(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {
        if (shouldResetLearning(data[0].timestamp)) {
            resetLearning()
        }

        val segments = findDataSegments(data)

        if (segments.isEmpty()) {
            copyRawToSmoothed(data)
            return data
        }

        aapsLogger.debug(LTag.GLUCOSE, "UKF: Processing ${segments.size} data segments")

        // Track new measurements across all segments.
        val previousTimestamp = lastProcessedTimestamp
        lastProcessedTimestamp = data[0].timestamp

        // Process each segment independently.
        for ((idx, segment) in segments.withIndex()) {
            val segmentSize = segment.endIdx - segment.startIdx + 1
            aapsLogger.debug(
                LTag.GLUCOSE,
                "UKF: Segment $idx: $segmentSize points " +
                    "(idx ${segment.startIdx} to ${segment.endIdx})"
            )

            processSegment(data, segment.startIdx, segment.endIdx, previousTimestamp)
        }

        // Fill any unprocessed points with raw values.
        for (i in data.indices) {
            if (data[i].smoothed == 0.0) {  // Not yet processed.
                data[i].smoothed = max(data[i].value, 39.0)
                data[i].trendArrow = TrendArrow.NONE
            }
        }

        // Periodic logging.
        if (sessionMeasurementCount % 100 == 0L && sessionMeasurementCount > 0) {
            val sessionOutlierRate =
                sessionOutlierCount.toDouble() / sessionMeasurementCount
            val avgInnovation =
                if (innovations.isNotEmpty()) innovations.average() else 0.0
            aapsLogger.info(
                LTag.GLUCOSE,
                "UKF: Session $sensorSessionId, $sessionMeasurementCount measurements, " +
                    "R=${String.format("%.1f", learnedR)} [ADAPTIVE], " +
                    "Q_glucose=${String.format("%.2f", Q[0])} [FIXED], " +
                    "Q_rate=${String.format("%.4f", Q[3])} [FIXED], " +
                    "AvgInnovation=${String.format("%.2f", avgInnovation)}, " +
                    "OutlierRate=${String.format("%.1f%%", sessionOutlierRate * 100)}"
            )
        }

        // Save if we processed new data.
        val newDataProcessed = data.any { it.timestamp > previousTimestamp }
        if (newDataProcessed && sessionMeasurementCount > 0) {
            val diagnostics = mapOf(
                "session_id" to sensorSessionId,
                "measurements" to sessionMeasurementCount,
                "outliers" to sessionOutlierCount,
                "outlier_rate" to (sessionOutlierCount.toDouble() / sessionMeasurementCount),
                "R_learned" to learnedR,
                "R_stdev_equiv" to sqrt(learnedR),
                "avg_innovation" to if (innovations.isNotEmpty()) innovations.average() else 0.0,
                "innovation_count" to innovations.size,
                "consecutive_outliers" to consecutiveOutliers,
                "segments_processed" to segments.size
            )
            aapsLogger.debug(LTag.GLUCOSE, "UKF_DIAGNOSTICS: $diagnostics")
        }

        if (newDataProcessed) {
            savePersistedParameters()
        }

        return data
    }

    /**
     * Process a single continuous segment of data.
     *
     * Runs a forward UKF pass and backward RTS smoother over the segment only.
     */
    private fun processSegment(
        data: MutableList<InMemoryGlucoseValue>,
        startIdx: Int,           // Newest point in segment.
        endIdx: Int,             // Oldest point in segment.
        previousTimestamp: Long  // For tracking new measurements.
    ) {
        val segmentSize = endIdx - startIdx + 1
        if (segmentSize < 2) {
            data[startIdx].smoothed = max(data[startIdx].value, 39.0)
            data[startIdx].trendArrow = TrendArrow.NONE
            return
        }

        // Initialize state from the oldest point in the segment.
        val initialGlucose = data[endIdx].value
        var initialRate = 0.0

        if (segmentSize >= 2 && endIdx > 0) {
            val dt = (data[endIdx - 1].timestamp - data[endIdx].timestamp) / MILLIS_PER_MINUTE
            if (dt in 3.0..7.0) {
                initialRate = (data[endIdx - 1].value - data[endIdx].value) / dt
                initialRate = initialRate.coerceIn(-4.0, 4.0)
            }
        }

        val x = doubleArrayOf(initialGlucose, initialRate)
        val P = doubleArrayOf(16.0, 0.0, 0.0, 1.0)
        var R = learnedR

        val forwardStates = ArrayDeque<FilterState>(segmentSize)
        val forwardResults = DoubleArray(segmentSize)
        forwardResults[segmentSize - 1] = x[0]

        var segmentNewMeasurements = 0
        var segmentOutliers = 0

        // Local 2-of-3 same-sign gate for trend persistence (>2σ).
        val recentSigns = ArrayDeque<Int>(3)

        // === FORWARD PASS (within segment only) ===
        for (i in (endIdx - 1) downTo startIdx) {
            val dt = (data[i].timestamp - data[i + 1].timestamp) / MILLIS_PER_MINUTE

            // Handle minor gaps within the segment.
            if (dt > MINOR_GAP_THRESHOLD && dt <= MAJOR_GAP_THRESHOLD) {
                x[1] *= rateDamp(dt)
                aapsLogger.debug(
                    LTag.GLUCOSE,
                    "UKF: Bridging ${String.format("%.1f", dt)} min gap within segment"
                )
            }

            // Covariance sanity checks.
            P[0] = P[0].coerceIn(0.1, MAX_GLUCOSE_VARIANCE)
            P[3] = P[3].coerceIn(0.001, MAX_RATE_VARIANCE)

            // Use the real dt here; all process noise comes from predict().
            val dtUsed = dt

            // One-step prediction with fixed Q (base prediction).
            val (xPredBase, PPredBase) = predict(x, P, Q, dtUsed)

            val z = data[i].value

            // Skip only error code values (e.g., 38 mg/dL).
            if (z <= 38.0) {
                // For smoothing, still record the pre-update state and prediction.
                val stateBefore = FilterState(
                    x.copyOf(),
                    P.copyOf(),
                    xPredBase.copyOf(),
                    PPredBase.copyOf(),
                    dtUsed
                )

                x[0] = xPredBase[0]
                x[1] = xPredBase[1]
                P[0] = PPredBase[0]
                P[1] = PPredBase[1]
                P[2] = PPredBase[2]
                P[3] = PPredBase[3]

                val resultIdx = i - startIdx
                forwardResults[resultIdx] = x[0]
                forwardStates.addFirst(stateBefore)
                continue
            }

            // --- Innovation stats (pre-inflation, for gating only) ---
            val innovation = z - xPredBase[0]
            val innovationVarianceRaw = PPredBase[0] + R
            val stdRaw = sqrt(innovationVarianceRaw)
            val normRaw = innovation / stdRaw
            val isNewData = data[i].timestamp > previousTimestamp

            // Maintain 2-of-3 same-sign gate for trend persistence at >2σ.
            val sign = when {
                normRaw > 0.0 -> 1
                normRaw < 0.0 -> -1
                else -> 0
            }

            if (recentSigns.size == 3) recentSigns.removeLast()
            recentSigns.addFirst(if (abs(normRaw) > 2.0) sign else 0)
            val sameSignCount = if (sign == 0) 0 else recentSigns.count { it == sign }
            val qInflateAllowed = sameSignCount >= 2 && sign != 0

            val absn = abs(normRaw)

            // --- Measurement noise inflation (R_eff) ---
            // Huber-like per-sample R inflation with soft caps.
            val rScale = 1.0 + max(0.0, absn - 2.0) // Grows linearly beyond 2σ.
            val R_eff = min(R * rScale, min(R + 100.0, R_EFF_MAX)) // Gentle ceiling.

            // --- Process noise inflation (Q) for real trends ---
            // Temporary Q inflation: prioritize rate agility, keep glucose bounded.
            val zScore = absn.coerceAtLeast(1.0)
            val qScale = if (qInflateAllowed) zScore.coerceIn(1.0, 3.0) else 1.0
            val tempQ = if (qScale > 1.0) {
                Q.copyOf().apply {
                    this[0] = Q[0] * min(qScale, 2.0) // Modest glucose variance.
                    this[3] = Q[3] * qScale          // Agile slope.
                }
            } else {
                Q
            }

            // Re-predict if Q inflated, then update with R_eff.
            val (xPredEff, PPredEff) =
                if (qScale > 1.0) predict(x, P, tempQ, dtUsed) else Pair(xPredBase, PPredBase)

            // Store prediction for RTS smoothing (uses the effective prediction).
            val stateBefore = FilterState(
                x.copyOf(),
                P.copyOf(),
                xPredEff.copyOf(),
                PPredEff.copyOf(),
                dtUsed
            )

            // Effective innovation variance used by the filter (PPredEff + R_eff).
            val innovationVarianceEff = PPredEff[0] + R_eff
            val mahalSqEff = (innovation * innovation) / innovationVarianceEff

            // Track predicted variance history for adaptive-R.
            predVarHistory.addFirst(PPredEff[0])
            if (predVarHistory.size > innovationWindow) predVarHistory.removeLast()

            // UKF update with effective parameters.
            update(xPredEff, PPredEff, z, R_eff, x, P)

            // Track innovations for adaptive-R and reset logic using effecgtive variance.
            trackInnovation(innovation, innovationVarianceEff)

            // Pause R learning during real trend and on very large residuals.
            val skipRUpdate = qInflateAllowed || absn > 3.0
            if (!skipRUpdate) {
                R = adaptMeasurementNoise(R, innovations, rawInnovationVariance)
            }

            // Diagnostics on outliers, using effective covariance.
            if (mahalSqEff > CHI_SQUARED_THRESHOLD || abs(innovation) > OUTLIER_ABSOLUTE) {
                aapsLogger.debug(
                    LTag.GLUCOSE,
                    "UKF: Outlier detected - χ²=${String.format("%.2f", mahalSqEff)}, " +
                        "innovation=${String.format("%.1f", innovation)}, " +
                        "P[0]=${String.format("%.1f", P[0])}"
                )
            }

            if (isNewData) {
                segmentNewMeasurements++
                sessionMeasurementCount++
                if (mahalSqEff > CHI_SQUARED_THRESHOLD || abs(innovation) > OUTLIER_ABSOLUTE) {
                    segmentOutliers++
                    sessionOutlierCount++
                }
            }

            // Logging with effective parameters (just switch to xPredEff for consistency).
            aapsLogger.warn(
                LTag.GLUCOSE,
                "UKF: live R=${String.format("%.1f", R)}, " +
                    "R_eff=${String.format("%.1f", R_eff)}, " +
                    "BG=${String.format("%.0f", z)}, " +
                    "predBG=${String.format("%.0f", xPredEff[0])}, " +
                    "innov=${String.format("%.1f", innovation)}, " +
                    "|ν|/σ=${String.format("%.1f", absn)}, " +
                    "qScale=${String.format("%.1f", qScale)}, " +
                    "P[0]=${String.format("%.1f", P[0])}, " +
                    "P[3]=${String.format("%.4f", P[3])}"
            )

            val resultIdx = i - startIdx
            forwardResults[resultIdx] = x[0]
            forwardStates.addFirst(stateBefore)
        }

        // Update learned R from the segment.
        learnedR = R

        // Log segment processing.
        if (segmentNewMeasurements > 0) {
            val segmentOutlierRate =
                segmentOutliers.toDouble() / segmentNewMeasurements
            aapsLogger.debug(
                LTag.GLUCOSE,
                "UKF: Segment processed $segmentNewMeasurements new measurements, " +
                    "$segmentOutliers outliers " +
                    "(${String.format("%.1f%%", segmentOutlierRate * 100)})"
            )
        }

        // === BACKWARD SMOOTHING (RTS) - within segment only ===
        val smoothedResults = forwardResults.copyOf()
        if (segmentSize >= 3 && forwardStates.isNotEmpty()) {
            val maxSmoothSteps = min(segmentSize - 1, forwardStates.size)
            var xSmooth = doubleArrayOf(forwardResults[0], x[1])

            for (i in 1..maxSmoothSteps) {
                val state = forwardStates[i - 1]
                val C = computeSmootherGain(state.P, state.PPred, state.dt)
                val dx0 = xSmooth[0] - state.xPred[0]
                val dx1 = xSmooth[1] - state.xPred[1]
                xSmooth[0] = forwardResults[i] + C[0] * dx0 + C[1] * dx1
                xSmooth[1] = state.x[1] + C[2] * dx0 + C[3] * dx1
                smoothedResults[i] = xSmooth[0]
            }
        }

        // Apply results to this segment.
        for (i in startIdx..endIdx) {
            val resultIdx = i - startIdx
            data[i].smoothed = max(smoothedResults[resultIdx], 39.0)
            data[i].trendArrow =
                if (i == startIdx) computeTrendArrow(x[1]) else TrendArrow.NONE
        }
    }

    // ============================================================
    // ADAPTIVE R ESTIMATION
    // ============================================================

    /**
     * Track innovation statistics for adaptive R estimation.
     *
     * Stores both normalized innovation squared and raw innovation squared in
     * fixed-size windows for robust variance estimation.
     */
    private fun trackInnovation(innovation: Double, innovationVariance: Double) {
        val normalizedSq = (innovation * innovation) / innovationVariance
        val rawSq = innovation * innovation

        innovations.addFirst(normalizedSq)
        rawInnovationVariance.addFirst(rawSq)

        if (innovations.size > innovationWindow) {
            innovations.removeLast()
        }

        if (rawInnovationVariance.size > innovationWindow) {
            rawInnovationVariance.removeLast()
        }
    }

    /**
     * Adaptive measurement noise estimation.
     *
     * Uses innovation-based adaptive estimation (IAE) with robust trimmed-median
     * statistics on both innovation variance and predicted variance:
     *
     * - Computes a robust estimate of Var(ν) from recent raw innovations.
     * - Computes a robust estimate of P_pred[0] from recent predicted variances.
     * - Derives a target R̂ = Var(ν) - P_pred[0], clamped to [R_MIN, R_MAX].
     * - Updates currentR toward R̂ using asymmetric gains (faster when R must
     *   increase than when it should decrease) and per-step multiplicative caps
     *   to avoid large jumps.
     *
     * Under ideal tuning, E[ν²] ≈ P_pred[0] + R ⇒ E[normalized_innovation²] ≈ 1.0.
     */
    private fun adaptMeasurementNoise(
        currentR: Double,
        innovations: ArrayDeque<Double>,  // Stores ν²/(P[0] + R).
        rawSq: ArrayDeque<Double>         // Stores ν².
    ): Double {
        if (innovations.size < 12 || predVarHistory.isEmpty()) return currentR
        /*
                fun trimmedMedian(v: List<Double>, trim: Double = 0.13): Double {
                    val s = v.sorted()
                    val k = (s.size * trim).toInt().coerceAtMost((s.size - 1) / 2)
                    val core = s.subList(k, s.size - k)
                    val mid = core.size / 2
                    return if (core.isEmpty()) {
                        0.0
                    } else if (core.size % 2 == 1) {
                        core[mid]
                    } else {
                        0.5 * (core[mid - 1] + core[mid])
                    }
                }

         */

        fun trimmedMean(v: List<Double>, trim: Double = 0.20): Double {
            if (v.isEmpty()) return 0.0
            val s = v.sorted()
            val k = (s.size * trim).toInt().coerceAtMost((s.size - 1) / 2)
            val core = s.subList(k, s.size - k)
            return core.average()
        }

        //val N = minOf(innovationWindow, minOf(innovations.size, predVarHistory.size))
        val N = innovations.size
        val mRaw = trimmedMean(rawSq.take(N))        // Robust Var(ν).
        val pyyMed = trimmedMean(predVarHistory.take(N)) // Robust P_pred[0].

        // Robust, decoupled target.
        val RhatRaw = (mRaw - pyyMed).coerceAtLeast(R_MIN) // Ensure positivity.
        val Rhat = RhatRaw.coerceIn(R_MIN, R_MAX)

        // Deadband around currentR to avoid chatter.
        val diff = Rhat - currentR
        //if (abs(diff) < 8.0) return currentR           // ≈ ±(≈3 mg/dL)^2.

        // Asymmetric gains and gentle EMA step.
        val goingUp = diff > 0.0
        val kup = 0.18
        val kdn = 0.12 //MP increased from 0.10
        val k = if (goingUp) kup else kdn
        val step = currentR + k * diff

        // Per-sample multiplicative clamp to avoid jumps.
        val upCap = if (goingUp) 1.20 else 1.00
        val dnCap = if (goingUp) 1.00 else 0.90
        val clamped = step
            .coerceIn(currentR * dnCap, currentR * upCap)
            .coerceIn(R_MIN, R_MAX)

        // Final smoothing to prevent ping-pong while keeping agility.
        val eta = 0.25
        return (1.0 - eta) * currentR + eta * clamped
    }

    // ============================================================
    // TREND ARROW COMPUTATION
    // ============================================================

    /**
     * Compute trend arrow from glucose rate of change.
     *
     * @param rate glucose rate in mg/dL/min.
     * @return corresponding trend arrow.
     */
    private fun computeTrendArrow(rate: Double): TrendArrow {
        return when {
            rate > 2.0 -> TrendArrow.DOUBLE_UP
            rate > 1.0 -> TrendArrow.SINGLE_UP
            rate > 0.5 -> TrendArrow.FORTY_FIVE_UP
            rate < -2.0 -> TrendArrow.DOUBLE_DOWN
            rate < -1.0 -> TrendArrow.SINGLE_DOWN
            rate < -0.5 -> TrendArrow.FORTY_FIVE_DOWN
            else -> TrendArrow.FLAT
        }
    }

    // ============================================================
    // UKF CORE FUNCTIONS
    // ============================================================

    /**
     * Compute Rauch–Tung–Striebel (RTS) smoother gain.
     *
     * The smoother gain C maps forward-filtered estimates to backward-smoothed estimates:
     * C = P · Fᵀ · P_pred⁻¹
     *
     * Where F is the state transition Jacobian:
     * F = [[1, dt], [0, damping]]
     *
     * @param P forward-filtered covariance (2x2).
     * @param PPred one-step-ahead predicted covariance (2x2).
     * @param dt time step (minutes).
     * @return smoother gain matrix C (2x2 in row-major).
     */
    private fun computeSmootherGain(
        P: DoubleArray,
        PPred: DoubleArray,
        dt: Double
    ): DoubleArray {
        // F = [[1, dt],
        //      [0, exp(-dt/τ)]].
        val damp = rateDamp(dt)

        // Compute P · Fᵀ.
        val PFt00 = P[0] + P[1] * dt
        val PFt01 = P[1] * damp
        val PFt10 = P[2] + P[3] * dt
        val PFt11 = P[3] * damp

        // Invert PPred (2x2).
        val det = PPred[0] * PPred[3] - PPred[1] * PPred[2]
        if (abs(det) < 1e-10) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)

        val inv00 = PPred[3] / det
        val inv01 = -PPred[1] / det
        val inv10 = -PPred[2] / det
        val inv11 = PPred[0] / det

        // C = P · Fᵀ · PPred^{-1}.
        return doubleArrayOf(
            PFt00 * inv00 + PFt01 * inv10,
            PFt00 * inv01 + PFt01 * inv11,
            PFt10 * inv00 + PFt11 * inv10,
            PFt10 * inv01 + PFt11 * inv11
        )
    }

    /**
     * UKF prediction step.
     *
     * Propagates state and covariance through the process model using the unscented transform:
     * 1. Generate sigma points from current state.
     * 2. Propagate each sigma point through process model f(x) = [G + Ġ·dt, Ġ·damping].
     * 3. Compute predicted mean and covariance from transformed sigma points.
     * 4. Add fixed process noise Q (scaled linearly with time).
     *
     * @param x current state [glucose, rate].
     * @param P current covariance (2x2 in row-major).
     * @param Q fixed process noise covariance (2x2 in row-major).
     * @param dt time step in minutes.
     * @return pair of (predicted state, predicted covariance).
     */
    private fun predict(
        x: DoubleArray,
        P: DoubleArray,
        Q: DoubleArray,
        dt: Double
    ): Pair<DoubleArray, DoubleArray> {
        // 1) Sigma points from current state.
        val sigmaPoints = generateSigmaPoints(x, P)

        // 2) Propagate through process model with dt-based rate damping.
        val sigmaPointsPred = Array(2 * n + 1) { DoubleArray(n) }
        val damp = rateDamp(dt)
        for (i in 0 until (2 * n + 1)) {
            // Glucose: G_{t+1} = G_t + Ġ_t · dt.
            sigmaPointsPred[i][0] = sigmaPoints[i][0] + sigmaPoints[i][1] * dt
            // Rate: Ġ_{t+1} = Ġ_t · exp(-dt/τ).
            sigmaPointsPred[i][1] = sigmaPoints[i][1] * damp
        }

        // 3) Predicted mean.
        val xPred = DoubleArray(n)
        for (i in 0 until (2 * n + 1)) {
            xPred[0] += Wm[i] * sigmaPointsPred[i][0]
            xPred[1] += Wm[i] * sigmaPointsPred[i][1]
        }

        // 4) Predicted covariance.
        val PPred = DoubleArray(4)
        for (i in 0 until (2 * n + 1)) {
            val dx0 = sigmaPointsPred[i][0] - xPred[0]
            val dx1 = sigmaPointsPred[i][1] - xPred[1]
            PPred[0] += Wc[i] * dx0 * dx0
            PPred[1] += Wc[i] * dx0 * dx1
            PPred[2] += Wc[i] * dx1 * dx0
            PPred[3] += Wc[i] * dx1 * dx1
        }

        // 5) Add process noise scaled linearly with time (as in original).
        val qScale = dt / 5.0
        PPred[0] += Q[0] * qScale
        PPred[3] += Q[3] * qScale

        // 6) Ensure positive definiteness.
        PPred[0] = max(PPred[0], 0.1)
        PPred[3] = max(PPred[3], 0.001)

        return Pair(xPred, PPred)
    }

    /**
     * UKF update step.
     *
     * Updates state and covariance using a new measurement:
     * 1. Generate sigma points from predicted state.
     * 2. Transform sigma points through measurement model h(x) = G.
     * 3. Compute innovation (measurement - prediction).
     * 4. Compute Kalman gain.
     * 5. Update state and covariance.
     *
     * @param xPred predicted state [glucose, rate].
     * @param PPred predicted covariance (2x2 in row-major).
     * @param z measurement (glucose reading in mg/dL).
     * @param R adaptive measurement noise variance.
     * @param x output: updated state (modified in place).
     * @param P output: updated covariance (modified in place).
     */
    private fun update(
        xPred: DoubleArray,
        PPred: DoubleArray,
        z: Double,
        R: Double,
        x: DoubleArray,
        P: DoubleArray
    ) {
        // Generate sigma points from predicted state.
        val sigmaPoints = generateSigmaPoints(xPred, PPred)
        val zSigma = DoubleArray(2 * n + 1)

        // Transform sigma points through measurement model (h(x) = glucose).
        for (i in 0 until 2 * n + 1) {
            zSigma[i] = sigmaPoints[i][0]
        }

        // Compute predicted measurement: z̄ = Σ W_i^(m) · Z_i.
        var zPred = 0.0
        for (i in 0 until 2 * n + 1) {
            zPred += Wm[i] * zSigma[i]
        }

        // Compute innovation covariance: Pzz = Σ W_i^(c) · (Z_i - z̄)² + R.
        var Pzz = 0.0
        for (i in 0 until 2 * n + 1) {
            val dz = zSigma[i] - zPred
            Pzz += Wc[i] * dz * dz
        }
        Pzz += R

        // Safety check to prevent division by zero or numerical instability.
        if (Pzz < 1e-6) {
            aapsLogger.warn(
                LTag.GLUCOSE,
                "UKF: Innovation covariance too small (Pzz=$Pzz), skipping update"
            )
            x[0] = xPred[0]
            x[1] = xPred[1]
            P[0] = PPred[0]
            P[1] = PPred[1]
            P[2] = PPred[2]
            P[3] = PPred[3]
            return
        }

        // Compute cross-covariance: Pxz = Σ W_i^(c) · (χ_i - x̄)(Z_i - z̄).
        val Pxz = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPoints[i][0] - xPred[0]
            val dx1 = sigmaPoints[i][1] - xPred[1]
            val dz = zSigma[i] - zPred
            Pxz[0] += Wc[i] * dx0 * dz
            Pxz[1] += Wc[i] * dx1 * dz
        }

        // Compute Kalman gain: K = Pxz / Pzz.
        val K = DoubleArray(n)
        K[0] = Pxz[0] / Pzz
        K[1] = Pxz[1] / Pzz

        // Update state: x = x̄ + K · (z - z̄).
        val innovation = z - zPred
        x[0] = xPred[0] + K[0] * innovation
        x[1] = xPred[1] + K[1] * innovation

        // Clamp rate to physiological range.
        x[1] = x[1].coerceIn(-4.0, 4.0)

        // Update covariance: P = P̄ - K · Pzz · Kᵀ.
        P[0] = PPred[0] - K[0] * Pzz * K[0]
        P[1] = PPred[1] - K[0] * Pzz * K[1]
        P[2] = PPred[2] - K[1] * Pzz * K[0]
        P[3] = PPred[3] - K[1] * Pzz * K[1]

        // Ensure positive definiteness.
        P[0] = max(P[0], 0.1)
        P[3] = max(P[3], 0.001)
    }

    /**
     * Generate sigma points using Merwe's scaled formulation.
     *
     * Creates 2n+1 sigma points around mean x with spread determined by covariance P:
     * - χ₀ = x (center point).
     * - χᵢ = x + γ·sqrt(P)_i for i = 1..n.
     * - χᵢ = x - γ·sqrt(P)_{i-n} for i = n+1..2n.
     *
     * Where γ = sqrt(n + λ) and λ is the scaling parameter.
     *
     * @param x mean state [glucose, rate].
     * @param P covariance (2x2 in row-major).
     * @return array of 2n+1 sigma points.
     */
    private fun generateSigmaPoints(
        x: DoubleArray,
        P: DoubleArray
    ): Array<DoubleArray> {
        val sigmaPoints = Array(2 * n + 1) { DoubleArray(n) }
        val sqrtP = matrixSqrt2x2(P)

        // Center sigma point.
        sigmaPoints[0][0] = x[0]
        sigmaPoints[0][1] = x[1]

        // Positive and negative perturbations.
        for (i in 0 until n) {
            sigmaPoints[i + 1][0] = x[0] + gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1][1] = x[1] + gamma * sqrtP[i * 2 + 1]

            sigmaPoints[i + 1 + n][0] = x[0] - gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1 + n][1] = x[1] - gamma * sqrtP[i * 2 + 1]
        }

        return sigmaPoints
    }

    /**
     * Compute matrix square root using Cholesky decomposition for a 2x2 SPD matrix.
     *
     * For a 2x2 symmetric positive definite matrix P, computes L such that L·Lᵀ = P.
     * Uses analytical Cholesky factorization:
     *
     * L = [[l11, 0],
     *      [l21, l22]]
     *
     * Where:
     * - l11 = sqrt(a)
     * - l21 = b / l11
     * - l22 = sqrt(d - l21²)
     *
     * Includes validation for numerical stability and non-positive-definite matrices.
     *
     * @param P covariance matrix [a, b, c, d] in row-major order.
     * @return lower-triangular Cholesky factor L in column-major order.
     */
    private fun matrixSqrt2x2(P: DoubleArray): DoubleArray {
        val a = P[0]
        val b = (P[1] + P[2]) / 2.0 // Enforce symmetry.
        val d = P[3]

        val l11 = sqrt(max(a, 1e-9))
        val l21 = b / l11
        val discriminant = d - l21 * l21
        if (discriminant < -1e-9) {
            aapsLogger.warn(
                LTag.GLUCOSE,
                "UKF: Non-positive-definite covariance, using fallback"
            )
            return doubleArrayOf(
                sqrt(max(a, 0.1)),
                0.0,
                0.0,
                sqrt(max(d, 0.01))
            )
        }

        val l22 = sqrt(max(discriminant, 1e-9))

        // Return in column-major order for easy extraction.
        return doubleArrayOf(l11, l21, 0.0, l22)
    }

    // ============================================================
    // UTILITY FUNCTIONS
    // ============================================================

    /**
     * Copy raw glucose values to the smoothed field (fallback).
     *
     * Used when insufficient data is available for filtering (< 2 readings) or
     * when the filter fails; ensures smoothed field is always populated.
     */
    private fun copyRawToSmoothed(data: MutableList<InMemoryGlucoseValue>) {
        for (reading in data) {
            reading.smoothed = max(reading.value, 39.0)
            reading.trendArrow = TrendArrow.NONE
        }
    }
}