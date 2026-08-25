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
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.smoothing.keys.UkfDoubleNonKey
import app.aaps.plugins.smoothing.keys.UkfIntNonKey
import app.aaps.plugins.smoothing.keys.UkfLongNonKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale
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
    preferences: Preferences,
    private val persistenceLayer: PersistenceLayer
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .icon(Icons.Default.Timeline)
        .pluginName(R.string.UKF_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_UKF),
    ownPreferences = listOf(UkfLongNonKey::class.java, UkfIntNonKey::class.java, UkfDoubleNonKey::class.java),
    aapsLogger, rh, preferences
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
    private val wm = DoubleArray(2 * n + 1)
    private val wc = DoubleArray(2 * n + 1)

    // FIXED process noise covariances - tuned for realistic glucose dynamics.
    // These values must accommodate meal responses (rapid rises) and insulin action (rapid falls).
    // Increased significantly from original to handle real-world glucose variability.
    private val q = doubleArrayOf(
        1.0, 0.0,     // Glucose process noise
        0.0, 0.35     // Rate process noise
    )

    // Initial measurement noise (conservative starting point).
    private val rInit = 25.0  // ~5 mg/dL std dev - assumes moderate sensor quality.

    // Adaptive R bounds (variance, mg/dL^2).
    private val rMin = 16.0   // ~4 mg/dL std dev - excellent sensor.
    private val rMax = 225.0  // ~15 mg/dL std dev - poor sensor.
    private val rEffMax = 400.0

    // R adaptation window length for innovation statistics.
    private val innovationWindow = 18  // ≈90 minutes at 5‑min intervals.

    // Chi-squared based outlier detection (99.99% confidence, 1 DOF).
    private val chiSquaredThreshold = 15.13  // Statistically rigorous.
    private val outlierAbsolute = 65.0        // Absolute safety limit (mg/dL).

    // Covariance limits (tighter for faster recovery).
    private val maxGlucoseVariance = 400.0  // Max 20 mg/dL std dev.
    private val maxRateVariance = 4.0       // Max 2 mg/dL/min std dev.

    // Innovation-based validation - detect parameter corruption.
    private val innovationResetThreshold = 12.0   // Reset if avg innovation > 12.
    private val innovationValidationSamples = 15  // Need 15 samples before validating.

    // Gap handling.
    private val minorGapThreshold = 7.0       // Minutes - bridge with prediction.
    private val majorGapThreshold = 60.0      // Minutes - segment data.
    private val rateDecayTimeConstant = 30.0 // Minutes - physiological decay.

    // Hoisted constant for millis → minutes conversion to avoid repeated literal expressions.
    private val millisPerMinute = 1000.0 * 60.0

    private fun rateDamp(dt: Double): Double = exp(-dt / rateDecayTimeConstant)

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
     * @property p state covariance before update (2x2 in row-major).
     * @property xPred predicted state [glucose, rate].
     * @property pPred predicted covariance (2x2 in row-major).
     * @property dt time step used for this prediction (minutes).
     */
    private data class FilterState(
        val x: DoubleArray,
        val p: DoubleArray,
        val xPred: DoubleArray,
        val pPred: DoubleArray,
        val dt: Double
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FilterState) return false
            if (!x.contentEquals(other.x)) return false
            if (!p.contentEquals(other.p)) return false
            if (!xPred.contentEquals(other.xPred)) return false
            if (!pPred.contentEquals(other.pPred)) return false
            return dt == other.dt
        }

        override fun hashCode(): Int {
            var result = x.contentHashCode()
            result = 31 * result + p.contentHashCode()
            result = 31 * result + xPred.contentHashCode()
            result = 31 * result + pPred.contentHashCode()
            result = 31 * result + dt.hashCode()
            return result
        }
    }

    // ============================================================
    // PERSISTENT STATE
    // ============================================================

    // Learned measurement noise (variance).
    private var learnedR = rInit

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
        wm[0] = lambda / (n + lambda)
        wc[0] = lambda / (n + lambda) + (1 - alpha * alpha + beta)
        val w = 1.0 / (2.0 * (n + lambda))
        for (i in 1 until 2 * n + 1) {
            wm[i] = w
            wc[i] = w
        }

        // Load persisted parameters.
        loadPersistedParameters()
    }

    override suspend fun onStart() {
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
            val lastSaved = preferences.get(UkfLongNonKey.LastSavedTimestamp)
            val savedSensorChange = preferences.get(UkfLongNonKey.LastSensorChangeTimestamp)

            if (lastSaved > 0) {
                lastSensorChangeTimestamp = savedSensorChange
                lastProcessedTimestamp = preferences.get(UkfLongNonKey.LastProcessedTimestamp)
                learnedR = preferences.get(UkfDoubleNonKey.LearnedR)
                sensorSessionId = preferences.get(UkfIntNonKey.SessionId)

                // Validate loaded R.
                if (learnedR !in rMin..rMax) {
                    aapsLogger.info(
                        LTag.GLUCOSE,
                        "UKF: Loaded R ($learnedR) out of bounds, resetting to R_INIT"
                    )
                    learnedR = rInit
                }

                aapsLogger.info(
                    LTag.GLUCOSE,
                    "UKF: Loaded session $sensorSessionId " +
                        "(R=${String.format(Locale.US, "%.1f", learnedR)}, " +
                        "Q_glucose=${String.format(Locale.US, "%.2f", q[0])} [FIXED], " +
                        "Q_rate=${String.format(Locale.US, "%.4f", q[3])} [FIXED])"
                )
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "UKF: Failed to load persisted parameters", e)
            // Reset to defaults on error.
            learnedR = rInit
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
            preferences.put(UkfLongNonKey.LastSavedTimestamp, System.currentTimeMillis())
            preferences.put(UkfLongNonKey.LastSensorChangeTimestamp, lastSensorChangeTimestamp)
            preferences.put(UkfLongNonKey.LastProcessedTimestamp, lastProcessedTimestamp)
            preferences.put(UkfDoubleNonKey.LearnedR, learnedR)
            preferences.put(UkfIntNonKey.SessionId, sensorSessionId)

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
    override suspend fun onStop() {
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

        val timeDiffMinutes = (currentTimestamp - lastProcessedTimestamp) / millisPerMinute

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
        if (innovations.size >= innovationValidationSamples) {
            val avgInnovation = innovations.average()
            if (avgInnovation > innovationResetThreshold) {
                aapsLogger.info(
                    LTag.GLUCOSE,
                    "UKF: Severely mis-tuned parameters " +
                        "(avg innovation: ${String.format(Locale.US, "%.1f", avgInnovation)}), " +
                        "resetting (R was ${String.format(Locale.US, "%.1f", learnedR)})"
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
        learnedR = rInit
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
                "R=${String.format(Locale.US, "%.1f", learnedR)}, " +
                "Q_glucose=${String.format(Locale.US, "%.2f", q[0])} [FIXED], " +
                "Q_rate=${String.format(Locale.US, "%.4f", q[3])} [FIXED])"
        )

        // Save the reset state.
        savePersistedParameters()
    }

    // ============================================================
    // MAIN FILTERING API
    // ============================================================

    override fun smooth(data: MutableList<InMemoryGlucoseValue>): MutableList<InMemoryGlucoseValue> {
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
            val timeDiff = (data[i].timestamp - data[i + 1].timestamp) / millisPerMinute

            // Segment at major gaps (>60 min), invalid spacing, or error code.
            if (timeDiff !in 2.0..majorGapThreshold || data[i].value == 38.0) {
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

        // Fill any unprocessed points with calibration-corrected raw values.
        for (i in data.indices) {
            if (data[i].smoothed == 0.0) {  // Not yet processed.
                data[i].smoothed = max(data[i].calibratedOrValue, 39.0)
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
                    "R=${String.format(Locale.US, "%.1f", learnedR)} [ADAPTIVE], " +
                    "Q_glucose=${String.format(Locale.US, "%.2f", q[0])} [FIXED], " +
                    "Q_rate=${String.format(Locale.US, "%.4f", q[3])} [FIXED], " +
                    "AvgInnovation=${String.format(Locale.US, "%.2f", avgInnovation)}, " +
                    "OutlierRate=${String.format(Locale.US, "%.1f%%", sessionOutlierRate * 100)}"
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
            data[startIdx].smoothed = max(data[startIdx].calibratedOrValue, 39.0)
            data[startIdx].trendArrow = TrendArrow.NONE
            return
        }

        // Initialize state from the oldest point in the segment.
        val initialGlucose = data[endIdx].calibratedOrValue
        var initialRate = 0.0

        if (endIdx > 0) {
            val dt = (data[endIdx - 1].timestamp - data[endIdx].timestamp) / millisPerMinute
            if (dt in 3.0..7.0) {
                initialRate = (data[endIdx - 1].calibratedOrValue - data[endIdx].calibratedOrValue) / dt
                initialRate = initialRate.coerceIn(-4.0, 4.0)
            }
        }

        val x = doubleArrayOf(initialGlucose, initialRate)
        val p = doubleArrayOf(16.0, 0.0, 0.0, 1.0)
        var r = learnedR

        val forwardStates = ArrayDeque<FilterState>(segmentSize)
        val forwardResults = DoubleArray(segmentSize)
        forwardResults[segmentSize - 1] = x[0]

        var segmentNewMeasurements = 0
        var segmentOutliers = 0

        // Local 2-of-3 same-sign gate for trend persistence (>2σ).
        val recentSigns = ArrayDeque<Int>(3)

        // === FORWARD PASS (within segment only) ===
        for (i in (endIdx - 1) downTo startIdx) {
            val dt = (data[i].timestamp - data[i + 1].timestamp) / millisPerMinute

            // Handle minor gaps within the segment.
            if (dt > minorGapThreshold && dt <= majorGapThreshold) {
                x[1] *= rateDamp(dt)
                aapsLogger.debug(
                    LTag.GLUCOSE,
                    "UKF: Bridging ${String.format(Locale.US, "%.1f", dt)} min gap within segment"
                )
            }

            // Covariance sanity checks.
            p[0] = p[0].coerceIn(0.1, maxGlucoseVariance)
            p[3] = p[3].coerceIn(0.001, maxRateVariance)

            // Use the real dt here; all process noise comes from predict().
            val dtUsed = dt

            // One-step prediction with fixed Q (base prediction).
            val (xPredBase, pPredBase) = predict(x, p, q, dtUsed)

            // Sentinel check (xDrip error code 38.0) must use raw .value, not calibrated;
            // the Kalman measurement itself uses .calibratedOrValue.
            val rawValue = data[i].value
            val z = data[i].calibratedOrValue

            // Skip only error code values (e.g., 38 mg/dL).
            if (rawValue <= 38.0) {
                // For smoothing, still record the pre-update state and prediction.
                val stateBefore = FilterState(
                    x.copyOf(),
                    p.copyOf(),
                    xPredBase.copyOf(),
                    pPredBase.copyOf(),
                    dtUsed
                )

                x[0] = xPredBase[0]
                x[1] = xPredBase[1]
                p[0] = pPredBase[0]
                p[1] = pPredBase[1]
                p[2] = pPredBase[2]
                p[3] = pPredBase[3]

                val resultIdx = i - startIdx
                forwardResults[resultIdx] = x[0]
                forwardStates.addFirst(stateBefore)
                continue
            }

            // --- Innovation stats (pre-inflation, for gating only) ---
            val innovation = z - xPredBase[0]
            val innovationVarianceRaw = pPredBase[0] + r
            val stdRaw = sqrt(innovationVarianceRaw)
            val normRaw = innovation / stdRaw
            val isNewData = data[i].timestamp > previousTimestamp

            // Maintain 2-of-3 same-sign gate for trend persistence at >2σ.
            val sign = when {
                normRaw > 0.0 -> 1
                normRaw < 0.0 -> -1
                else          -> 0
            }

            if (recentSigns.size == 3) recentSigns.removeLast()
            recentSigns.addFirst(if (abs(normRaw) > 2.0) sign else 0)
            val sameSignCount = if (sign == 0) 0 else recentSigns.count { it == sign }
            val qInflateAllowed = sameSignCount >= 2

            val absn = abs(normRaw)

            // --- Measurement noise inflation (R_eff) ---
            // Huber-like per-sample R inflation with soft caps.
            val rScale = 1.0 + max(0.0, absn - 2.0) // Grows linearly beyond 2σ.
            val rEff = min(r * rScale, min(r + 100.0, rEffMax)) // Gentle ceiling.

            // --- Process noise inflation (Q) for real trends ---
            // Temporary Q inflation: prioritize rate agility, keep glucose bounded.
            val zScore = absn.coerceAtLeast(1.0)
            val qScale = if (qInflateAllowed) zScore.coerceIn(1.0, 3.0) else 1.0
            val tempQ = if (qScale > 1.0) {
                q.copyOf().apply {
                    this[0] = q[0] * min(qScale, 2.0) // Modest glucose variance.
                    this[3] = q[3] * qScale          // Agile slope.
                }
            } else {
                q
            }

            // Re-predict if Q inflated, then update with R_eff.
            val (xPredEff, pPredEff) =
                if (qScale > 1.0) predict(x, p, tempQ, dtUsed) else Pair(xPredBase, pPredBase)

            // Store prediction for RTS smoothing (uses the effective prediction).
            val stateBefore = FilterState(
                x.copyOf(),
                p.copyOf(),
                xPredEff.copyOf(),
                pPredEff.copyOf(),
                dtUsed
            )

            // Effective innovation variance used by the filter (PPredEff + R_eff).
            val innovationVarianceEff = pPredEff[0] + rEff
            val mahalSqEff = (innovation * innovation) / innovationVarianceEff

            // Track predicted variance history for adaptive-R.
            predVarHistory.addFirst(pPredEff[0])
            if (predVarHistory.size > innovationWindow) predVarHistory.removeLast()

            // UKF update with effective parameters.
            update(xPredEff, pPredEff, z, rEff, x, p)

            // Track innovations for adaptive-R and reset logic using effecgtive variance.
            trackInnovation(innovation, innovationVarianceEff)

            // Pause R learning during real trend and on very large residuals.
            val skipRUpdate = qInflateAllowed || absn > 3.0
            if (!skipRUpdate) {
                r = adaptMeasurementNoise(r, innovations, rawInnovationVariance)
            }

            // Diagnostics on outliers, using effective covariance.
            if (mahalSqEff > chiSquaredThreshold || abs(innovation) > outlierAbsolute) {
                aapsLogger.debug(
                    LTag.GLUCOSE,
                    "UKF: Outlier detected - χ²=${String.format(Locale.US, "%.2f", mahalSqEff)}, " +
                        "innovation=${String.format(Locale.US, "%.1f", innovation)}, " +
                        "P[0]=${String.format(Locale.US, "%.1f", p[0])}"
                )
            }

            if (isNewData) {
                segmentNewMeasurements++
                sessionMeasurementCount++
                if (mahalSqEff > chiSquaredThreshold || abs(innovation) > outlierAbsolute) {
                    segmentOutliers++
                    sessionOutlierCount++
                }
            }

            // Logging with effective parameters (just switch to xPredEff for consistency).
            aapsLogger.warn(
                LTag.GLUCOSE,
                "UKF: live R=${String.format(Locale.US, "%.1f", r)}, " +
                    "R_eff=${String.format(Locale.US, "%.1f", rEff)}, " +
                    "BG=${String.format(Locale.US, "%.0f", z)}, " +
                    "predBG=${String.format(Locale.US, "%.0f", xPredEff[0])}, " +
                    "innov=${String.format(Locale.US, "%.1f", innovation)}, " +
                    "|ν|/σ=${String.format(Locale.US, "%.1f", absn)}, " +
                    "qScale=${String.format(Locale.US, "%.1f", qScale)}, " +
                    "P[0]=${String.format(Locale.US, "%.1f", p[0])}, " +
                    "P[3]=${String.format(Locale.US, "%.4f", p[3])}"
            )

            val resultIdx = i - startIdx
            forwardResults[resultIdx] = x[0]
            forwardStates.addFirst(stateBefore)
        }

        // Update learned R from the segment.
        learnedR = r

        // Log segment processing.
        if (segmentNewMeasurements > 0) {
            val segmentOutlierRate =
                segmentOutliers.toDouble() / segmentNewMeasurements
            aapsLogger.debug(
                LTag.GLUCOSE,
                "UKF: Segment processed $segmentNewMeasurements new measurements, " +
                    "$segmentOutliers outliers " +
                    "(${String.format(Locale.US, "%.1f%%", segmentOutlierRate * 100)})"
            )
        }

        // === BACKWARD SMOOTHING (RTS) - within segment only ===
        val smoothedResults = forwardResults.copyOf()
        if (segmentSize >= 3 && forwardStates.isNotEmpty()) {
            val maxSmoothSteps = min(segmentSize - 1, forwardStates.size)
            val xSmooth = doubleArrayOf(forwardResults[0], x[1])

            for (i in 1..maxSmoothSteps) {
                val state = forwardStates[i - 1]
                val c = computeSmootherGain(state.p, state.pPred, state.dt)
                val dx0 = xSmooth[0] - state.xPred[0]
                val dx1 = xSmooth[1] - state.xPred[1]
                xSmooth[0] = forwardResults[i] + c[0] * dx0 + c[1] * dx1
                xSmooth[1] = state.x[1] + c[2] * dx0 + c[3] * dx1
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

        fun trimmedMean(v: List<Double>, trim: Double = 0.20): Double {
            if (v.isEmpty()) return 0.0
            val s = v.sorted()
            val k = (s.size * trim).toInt().coerceAtMost((s.size - 1) / 2)
            val core = s.subList(k, s.size - k)
            return core.average()
        }

        val nSize = innovations.size
        val mRaw = trimmedMean(rawSq.take(nSize))        // Robust Var(ν).
        val pyyMed = trimmedMean(predVarHistory.take(nSize)) // Robust P_pred[0].

        // Robust, decoupled target.
        val rHatRaw = (mRaw - pyyMed).coerceAtLeast(rMin) // Ensure positivity.
        val rHat = rHatRaw.coerceIn(rMin, rMax)

        // Asymmetric gains and gentle EMA step.
        val goingUp = rHat > currentR
        val kup = 0.18
        val kdn = 0.12 //MP increased from 0.10
        val k = if (goingUp) kup else kdn
        val step = currentR + k * (rHat - currentR)

        // Per-sample multiplicative clamp to avoid jumps.
        val upCap = if (goingUp) 1.20 else 1.00
        val dnCap = if (goingUp) 1.00 else 0.90
        val clamped = step
            .coerceIn(currentR * dnCap, currentR * upCap)
            .coerceIn(rMin, rMax)

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
            rate > 2.0  -> TrendArrow.DOUBLE_UP
            rate > 1.0  -> TrendArrow.SINGLE_UP
            rate > 0.5  -> TrendArrow.FORTY_FIVE_UP
            rate < -2.0 -> TrendArrow.DOUBLE_DOWN
            rate < -1.0 -> TrendArrow.SINGLE_DOWN
            rate < -0.5 -> TrendArrow.FORTY_FIVE_DOWN
            else        -> TrendArrow.FLAT
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
     * @param p forward-filtered covariance (2x2).
     * @param pPred one-step-ahead predicted covariance (2x2).
     * @param dt time step (minutes).
     * @return smoother gain matrix C (2x2 in row-major).
     */
    private fun computeSmootherGain(
        p: DoubleArray,
        pPred: DoubleArray,
        dt: Double
    ): DoubleArray {
        // F = [[1, dt],
        //      [0, exp(-dt/τ)]].
        val damp = rateDamp(dt)

        // Compute P · Fᵀ.
        val pfT00 = p[0] + p[1] * dt
        val pfT01 = p[1] * damp
        val pfT10 = p[2] + p[3] * dt
        val pfT11 = p[3] * damp

        // Invert PPred (2x2).
        val det = pPred[0] * pPred[3] - pPred[1] * pPred[2]
        if (abs(det) < 1e-10) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)

        val inv00 = pPred[3] / det
        val inv01 = -pPred[1] / det
        val inv10 = -pPred[2] / det
        val inv11 = pPred[0] / det

        // C = P · Fᵀ · PPred^{-1}.
        return doubleArrayOf(
            pfT00 * inv00 + pfT01 * inv10,
            pfT00 * inv01 + pfT01 * inv11,
            pfT10 * inv00 + pfT11 * inv10,
            pfT10 * inv01 + pfT11 * inv11
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
     * @param p current covariance (2x2 in row-major).
     * @param q fixed process noise covariance (2x2 in row-major).
     * @param dt time step in minutes.
     * @return pair of (predicted state, predicted covariance).
     */
    private fun predict(
        x: DoubleArray,
        p: DoubleArray,
        q: DoubleArray,
        dt: Double
    ): Pair<DoubleArray, DoubleArray> {
        // 1) Sigma points from current state.
        val sigmaPoints = generateSigmaPoints(x, p)

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
            xPred[0] += wm[i] * sigmaPointsPred[i][0]
            xPred[1] += wm[i] * sigmaPointsPred[i][1]
        }

        // 4) Predicted covariance.
        val pPred = DoubleArray(4)
        for (i in 0 until (2 * n + 1)) {
            val dx0 = sigmaPointsPred[i][0] - xPred[0]
            val dx1 = sigmaPointsPred[i][1] - xPred[1]
            pPred[0] += wc[i] * dx0 * dx0
            pPred[1] += wc[i] * dx0 * dx1
            pPred[2] += wc[i] * dx1 * dx0
            pPred[3] += wc[i] * dx1 * dx1
        }

        // 5) Add process noise scaled linearly with time (as in original).
        val qScale = dt / 5.0
        pPred[0] += q[0] * qScale
        pPred[3] += q[3] * qScale

        // 6) Ensure positive definiteness.
        pPred[0] = max(pPred[0], 0.1)
        pPred[3] = max(pPred[3], 0.001)

        return Pair(xPred, pPred)
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
     * @param pPred predicted covariance (2x2 in row-major).
     * @param z measurement (glucose reading in mg/dL).
     * @param r adaptive measurement noise variance.
     * @param x output: updated state (modified in place).
     * @param p output: updated covariance (modified in place).
     */
    private fun update(
        xPred: DoubleArray,
        pPred: DoubleArray,
        z: Double,
        r: Double,
        x: DoubleArray,
        p: DoubleArray
    ) {
        // Generate sigma points from predicted state.
        val sigmaPoints = generateSigmaPoints(xPred, pPred)
        val zSigma = DoubleArray(2 * n + 1)

        // Transform sigma points through measurement model (h(x) = glucose).
        for (i in 0 until 2 * n + 1) {
            zSigma[i] = sigmaPoints[i][0]
        }

        // Compute predicted measurement: z̄ = Σ W_i^(m) · Z_i.
        var zPred = 0.0
        for (i in 0 until 2 * n + 1) {
            zPred += wm[i] * zSigma[i]
        }

        // Compute innovation covariance: Pzz = Σ W_i^(c) · (Z_i - z̄)² + R.
        var pzz = 0.0
        for (i in 0 until 2 * n + 1) {
            val dz = zSigma[i] - zPred
            pzz += wc[i] * dz * dz
        }
        pzz += r

        // Safety check to prevent division by zero or numerical instability.
        if (pzz < 1e-6) {
            aapsLogger.warn(
                LTag.GLUCOSE,
                "UKF: Innovation covariance too small (Pzz=$pzz), skipping update"
            )
            x[0] = xPred[0]
            x[1] = xPred[1]
            p[0] = pPred[0]
            p[1] = pPred[1]
            p[2] = pPred[2]
            p[3] = pPred[3]
            return
        }

        // Compute cross-covariance: Pxz = Σ W_i^(c) · (χ_i - x̄)(Z_i - z̄).
        val pxz = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPoints[i][0] - xPred[0]
            val dx1 = sigmaPoints[i][1] - xPred[1]
            val dz = zSigma[i] - zPred
            pxz[0] += wc[i] * dx0 * dz
            pxz[1] += wc[i] * dx1 * dz
        }

        // Compute Kalman gain: K = Pxz / Pzz.
        val k = DoubleArray(n)
        k[0] = pxz[0] / pzz
        k[1] = pxz[1] / pzz

        // Update state: x = x̄ + K · (z - z̄).
        val innovation = z - zPred
        x[0] = xPred[0] + k[0] * innovation
        x[1] = xPred[1] + k[1] * innovation

        // Clamp rate to physiological range.
        x[1] = x[1].coerceIn(-4.0, 4.0)

        // Update covariance: P = P̄ - K · Pzz · Kᵀ.
        p[0] = pPred[0] - k[0] * pzz * k[0]
        p[1] = pPred[1] - k[0] * pzz * k[1]
        p[2] = pPred[2] - k[1] * pzz * k[0]
        p[3] = pPred[3] - k[1] * pzz * k[1]

        // Ensure positive definiteness.
        p[0] = max(p[0], 0.1)
        p[3] = max(p[3], 0.001)
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
     * @param p covariance (2x2 in row-major).
     * @return array of 2n+1 sigma points.
     */
    private fun generateSigmaPoints(
        x: DoubleArray,
        p: DoubleArray
    ): Array<DoubleArray> {
        val sigmaPoints = Array(2 * n + 1) { DoubleArray(n) }
        val sqrtP = matrixSqrt2x2(p)

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
     * @param p covariance matrix [a, b, c, d] in row-major order.
     * @return lower-triangular Cholesky factor L in column-major order.
     */
    private fun matrixSqrt2x2(p: DoubleArray): DoubleArray {
        val a = p[0]
        val b = (p[1] + p[2]) / 2.0 // Enforce symmetry.
        val d = p[3]

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
            reading.smoothed = max(reading.calibratedOrValue, 39.0)
            reading.trendArrow = TrendArrow.NONE
        }
    }
}