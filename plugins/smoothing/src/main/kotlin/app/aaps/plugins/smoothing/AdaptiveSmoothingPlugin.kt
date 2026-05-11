package app.aaps.plugins.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.awaitInitialized
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.smoothing.SmoothingContext
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.core.keys.DoubleNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Adaptive UKF smoothing plugin.
 *
 * Combines:
 * 1. Unscented Kalman Filter (UKF) for signal processing and trend estimation.
 * 2. Rule-based safety logic for compression artifacts and low-glucose handling.
 */
@Singleton
class AdaptiveSmoothingPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val iobCobCalculator: IobCobCalculator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SMOOTHING)
        .icon(IcStats)
        .pluginName(R.string.adaptive_smoothing_name)
        .shortName(R.string.smoothing_shortname)
        .description(R.string.description_adaptive_smoothing),
    aapsLogger, rh
), Smoothing {

    // ============================================================
    // UKF CONFIGURATION & PARAMETERS
    // ============================================================

    private val n = 2 // State dimension [G, Ġ]
    private val alpha = 1.00
    private val beta = 0.0
    private val kappa = 3.0
    private val lambda = alpha * alpha * (n + kappa) - n
    private val gamma = sqrt(n + lambda)

    // Sigma point weights
    private val Wm = DoubleArray(2 * n + 1)
    private val Wc = DoubleArray(2 * n + 1)

    // FIXED process noise (Physiological Limits)
    private val Q_FIXED = doubleArrayOf(
        1.0, 0.0,     // Glucose process noise: ~2.4 mg/dL std dev per 5 min
        0.0, 0.40     // Rate process noise: ~0.24 mg/dL/min std dev
    )

    // Adaptive Measurement Noise (R) Limits (learned default = [DoubleNonKey.UkfLearnedR])
    private val R_MIN = 16.0
    private val R_MAX = 196.0

    // Adaptation Logic
    private val innovationWindow = 48
    private val RATE_DAMPING = 0.98

    // Processing state
    private var learnedR = DoubleNonKey.UkfLearnedR.defaultValue
    private val innovations = ArrayDeque<Double>(innovationWindow + 1)
    private val rawInnovationVariance = ArrayDeque<Double>(innovationWindow + 1)
    private var lastProcessedTimestamp: Long = 0
    private var lastSensorChangeTimestamp: Long = 0

    private val smoothingSupervisor = SupervisorJob()
    private val smoothingScope = CoroutineScope(smoothingSupervisor + Dispatchers.IO)

    private val resetRequested = AtomicBoolean(false)
    private var sensorObservationJob: Job? = null
    private var sensorBackfillJob: Job? = null

    // Safety Context
    private data class GlycemicContext(
        val cv: Double,
        val zone: GlycemicZone,
        val currentBg: Double,
        val iob: Double,
        val isNight: Boolean,
        val rawDelta: Double // Heuristic delta for rules
    )

    private enum class GlycemicZone { HYPO, LOW_NORMAL, TARGET, HYPER }

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {
        initSigmaWeights()
        loadPersistedParameters()
        sensorObservationJob = smoothingScope.launch {
            if (!config.awaitInitialized(30_000L)) {
                aapsLogger.warn(LTag.GLUCOSE, "AdaptiveSmoothing: config not initialized; sensor TE observation not started")
                return@launch
            }
            try {
                persistenceLayer.observeChanges(TE::class.java)
                    .debounce(SENSOR_CHANGE_DEBOUNCE_SECONDS * 1000)
                    .collect { checkForSensorChange() }
            } catch (t: Throwable) {
                aapsLogger.error(LTag.GLUCOSE, "AdaptiveSmoothing: sensor subscription error", t)
            }
        }
        sensorBackfillJob = smoothingScope.launch {
            if (!config.awaitInitialized(30_000L)) return@launch
            runCatching { loadInitialSensorChangeFromDb() }
                .onFailure { t -> aapsLogger.error(LTag.GLUCOSE, "AdaptiveSmoothing: loadLastSensorChange error", t) }
        }
    }

    private fun initSigmaWeights() {
        Wm[0] = lambda / (n + lambda)
        Wc[0] = lambda / (n + lambda) + (1 - alpha * alpha + beta)
        val w = 1.0 / (2.0 * (n + lambda))
        for (i in 1 until 2 * n + 1) {
            Wm[i] = w
            Wc[i] = w
        }
    }

    override fun onStop() {
        sensorObservationJob?.cancel()
        sensorBackfillJob?.cancel()
        smoothingSupervisor.cancelChildren()
        super.onStop()
    }

    // ============================================================
    // MAIN SMOOTHING LOOP
    // ============================================================

    override suspend fun smooth(
        data: MutableList<InMemoryGlucoseValue>,
        context: SmoothingContext
    ): MutableList<InMemoryGlucoseValue> {
        if (data.size < 2) {
            copyRawToSmoothed(data)
            sanitizeOutput(data)
            return data
        }

        try {
            if (shouldResetLearning(data[0].timestamp)) {
                resetLearning()
            }

            val previousTimestamp = lastProcessedTimestamp
            lastProcessedTimestamp = data[0].timestamp

            val cachedIobTotalU = context.cachedTotalIobUnits ?: run {
                val bolusIob = iobCobCalculator.calculateIobFromBolus().iob
                val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().iob
                bolusIob + basalIob
            }
            processHybridSegment(data, cachedIobTotalU)

            val newDataProcessed = data.any { it.timestamp > previousTimestamp }
            if (newDataProcessed) {
                savePersistedParameters()
            }

            sanitizeOutput(data)
            return data

        } catch (e: Exception) {
            aapsLogger.error(LTag.GLUCOSE, "HybridSmoothing: Error, falling back to raw", e)
            copyRawToSmoothed(data)
            sanitizeOutput(data)
            return data
        }
    }

    private fun processHybridSegment(
        data: MutableList<InMemoryGlucoseValue>,
        cachedIobTotalU: Double,
    ) {
        // We will process the list from Oldest to Newest for the Filter (Time forward)
        // data list is usually Newest [0] -> Oldest [N]
        // So we iterate backwards through the list indices

        // Initialize State
        val startIdx = data.lastIndex
        val x = doubleArrayOf(data[startIdx].value, 0.0) // Initial state [G, 0]
        val stateCovariance = doubleArrayOf(16.0, 0.0, 0.0, 1.0) // Initial covariance (2×2 row-major)
        var measurementNoiseR = learnedR

        // --- FORWARD PASS (FILTER) ---
        for (i in startIdx downTo 0) {
            val z = data[i].value
            val timestamp = data[i].timestamp

            // Calculate dt (Time since last step)
            // If i == startIdx (oldest), dt is 0 or estimated.
            val dt = if (i < startIdx) {
                (timestamp - data[i + 1].timestamp) / (1000.0 * 60.0)
            } else {
                5.0 // Assumption for first point
            }
            
            val dtClamped = dt.coerceIn(1.0, 15.0) // Clamp to reasonable limits

            // --- ADAPTIVE SAFETY GUARDRAILS ---
            // Calculate heuristic context for this point
            val ctx = calculateGlycemicContext(data, i, cachedIobTotalU)
            
            // Check for Blocking Artifacts (Compression Lows)
            val isCompression = isCompressionArtifactCandidate(ctx, data, i)

            // Check for Hypo Safety Bypass
            val isHypoCritical = ctx.currentBg < 70.0

            // --- UKF STEP ---

            // 1. Standard Prediction (Baseline Physiology)
            var (xPred, predictedCovariance) = predict(x, stateCovariance, Q_FIXED, dtClamped)
            
            // 2. DYNAMIC MANEUVER DETECTION (Zero-Lag Hyper)
            // Large positive innovation: inflate Q so the filter tracks fast rises (meals/stress).
            
            val preFitInnovation = z - xPred[0]
            val preFitSigma = sqrt(predictedCovariance[0] + measurementNoiseR) // Expected deviation
            val normInnovation = preFitInnovation / preFitSigma
            
            // Condition: Rapid Rise (Innovation > 2.5 sigma) AND data is higher than prediction
            // We specificallly target rises (z > xPred) to avoid lag on meals.
            // Drops are handled by Safety Guards/Kinematics.
            val isRapidManeuver = (normInnovation > 2.5 && preFitInnovation > 0)
            
            if (isRapidManeuver) {
                 aapsLogger.debug(LTag.GLUCOSE, "HybridSmoothing: RAPID RISE DETECTED (Innov=${preFitInnovation.toInt()}). Inflating Q for Zero-Lag.")
                 
                 // Inflate Q_rate massively to allow instant velocity adaptation
                 val qAdaptive = Q_FIXED.clone()
                 qAdaptive[3] *= 50.0 // Allow huge rate change
                 qAdaptive[0] *= 2.0  // Slight position looseness
                 
                 // Re-Run Prediction with Inflated Q
                 val result = predict(x, stateCovariance, qAdaptive, dtClamped)
                 xPred = result.first
                 predictedCovariance = result.second
            }

            // 3. Update (Measurement)
            // Handling Artifacts:
            // If Compression: We ignore the measurement Z, and rely purely on Prediction (Blind Update)
            // Or we create a synthetic measurement equal to prediction.
            
            if (isCompression) {
                aapsLogger.warn(LTag.GLUCOSE, "HybridSmoothing: COMPRESSION BLOCKED at ${z.toInt()} mg/dL. Holding prediction.")
                // Blind update: Keep xPred as x, but don't collapse P (uncertainty grows)
                // Effectively: x = xPred; covariance carries predicted uncertainty
                x[0] = xPred[0]
                x[1] = xPred[1]
                stateCovariance[0] = predictedCovariance[0]; stateCovariance[1] = predictedCovariance[1]; stateCovariance[2] = predictedCovariance[2]; stateCovariance[3] = predictedCovariance[3]
                
                // Override smoothed value for this point
                data[i].smoothed = x[0] // Projected value
                
            } else {
                // Normal Update
                // Calculate Innovation for R adaptation
                val innovation = z - xPred[0]
                val innovationVariance = predictedCovariance[0] + measurementNoiseR

                // Adapt R (Noise)
                measurementNoiseR = adaptMeasurementNoise(measurementNoiseR, innovations, rawInnovationVariance)
                trackInnovation(innovation, innovationVariance)
                
                // Execute Update
                update(xPred, predictedCovariance, z, measurementNoiseR, x, stateCovariance)

                // Hypo kinematics: avoid masking real drops when velocity is steep.
                // 1. Predict Future BG (20 min horizon) using current Velocity state
                val velocity = x[1] // mg/dL per min
                val predictedBg20min = x[0] + (velocity * 20.0)
                
                // 2. Detect "Real Proportion to Hypo" (Kinetic Hypo Risk)
                // Conditions:
                // - Future is critical (< 55) OR
                // - Current is low (< 80) AND dropping fast (<-1.5) OR
                // - Current is dropping VERY fast (<-3.0) regardless of level
                val isKineticHypo = (predictedBg20min < 55.0) || 
                                   (z < 80.0 && velocity < -1.5) || 
                                   (velocity < -3.0)

                if (isKineticHypo) {
                     if (x[0] > z) {
                         x[0] = z
                     }
                     if (velocity < -2.0) {
                         x[0] += (velocity * 2.0)
                     }
                     
                     aapsLogger.debug(LTag.GLUCOSE, "HybridSmoothing: KINETIC HYPO DETECTED! Vel=${velocity}, Pred20=${predictedBg20min}. Forcing low.")
                } else if (isHypoCritical && x[0] > z + 5.0) {
                     // Standard Hypo Safety fallback (as before)
                     x[0] = (x[0] + z) / 2.0
                }
                
                data[i].smoothed = x[0]
            }

            // Determine Trend Arrow from Rate (State x[1])
            // This is superior to standard Delta
            data[i].trendArrow = computeTrendArrow(x[1])
        }

        learnedR = measurementNoiseR
    }

    // ============================================================
    // HEURISTIC SAFETY LOGIC
    // ============================================================

    private fun calculateGlycemicContext(data: List<InMemoryGlucoseValue>, index: Int, cachedIobTotalU: Double): GlycemicContext {
        // Need next points (future/newest) relative to index? 
        // No, 'data' is Newest..Oldest.
        // If we are at 'i', older points are i+1, i+2.
        
        val valCur = data[index].value
        val valOld1 = if (index + 1 < data.size) data[index+1].value else valCur
        
        // Heuristic Delta (Raw) 
        val rawDelta = valCur - valOld1
        
        // IOB Safety (current IOB; same for all points in this pass)
        val iob = cachedIobTotalU

        // Night
        val now = java.util.Calendar.getInstance()
        now.timeInMillis = data[index].timestamp
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val isNight = hour < 7 || hour >= 23

        val zone = when {
            valCur < 70 -> GlycemicZone.HYPO
            valCur < 90 -> GlycemicZone.LOW_NORMAL
            valCur < 180 -> GlycemicZone.TARGET
            else -> GlycemicZone.HYPER
        }
        
        return GlycemicContext(
            cv = 0.0, // Simplified for realtime check
            zone = zone,
            currentBg = valCur,
            iob = iob,
            isNight = isNight,
            rawDelta = rawDelta
        )
    }

    private fun isCompressionArtifactCandidate(ctx: GlycemicContext, data: List<InMemoryGlucoseValue>, index: Int): Boolean {
        // 1. Massive Drop Check
        // If raw delta is impossibly steep negative e.g. -20mg/dl in 5 mins
        val dropThreshold = if (ctx.isNight) -15.0 else -25.0
        
        if (ctx.rawDelta < dropThreshold) {
            // 2. Verify Physiological Feasibility
            // If IOB is low, such a drop is likely fake.
            if (ctx.iob < 3.0) {
                 return true
            }
        }
        return false
    }

    // ============================================================
    // UKF MATHEMATICS (Unscented Transform)
    // ============================================================

    private fun predict(x: DoubleArray, covariance: DoubleArray, Q: DoubleArray, dt: Double): Pair<DoubleArray, DoubleArray> {
        // Generate Sigma Points
        val sigmaPoints = generateSigmaPoints(x, covariance)
        val sigmaPointsPred = Array(2 * n + 1) { DoubleArray(n) }

        // Propagate (Model: G + G_dot*dt)
        for (i in 0 until 2 * n + 1) {
            sigmaPointsPred[i][0] = sigmaPoints[i][0] + sigmaPoints[i][1] * dt
            sigmaPointsPred[i][1] = sigmaPoints[i][1] * RATE_DAMPING
        }

        // Recombine Mean
        val xPred = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            xPred[0] += Wm[i] * sigmaPointsPred[i][0]
            xPred[1] += Wm[i] * sigmaPointsPred[i][1]
        }

        // Recombine Covariance
        val predictedCovarianceMatrix = DoubleArray(4)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPointsPred[i][0] - xPred[0]
            val dx1 = sigmaPointsPred[i][1] - xPred[1]
            predictedCovarianceMatrix[0] += Wc[i] * dx0 * dx0
            predictedCovarianceMatrix[1] += Wc[i] * dx0 * dx1
            predictedCovarianceMatrix[2] += Wc[i] * dx1 * dx0
            predictedCovarianceMatrix[3] += Wc[i] * dx1 * dx1
        }

        // Add Process Noise (Scaled by time)
        val qScale = dt / 5.0
        predictedCovarianceMatrix[0] += Q[0] * qScale
        predictedCovarianceMatrix[3] += Q[3] * qScale
        
        predictedCovarianceMatrix[0] = max(predictedCovarianceMatrix[0], 0.1)
        predictedCovarianceMatrix[3] = max(predictedCovarianceMatrix[3], 0.001)

        return Pair(xPred, predictedCovarianceMatrix)
    }

    private fun update(
        xPred: DoubleArray,
        predictedCovariance: DoubleArray,
        z: Double,
        measurementNoiseVariance: Double,
        x: DoubleArray,
        covariance: DoubleArray,
    ) {
        val sigmaPoints = generateSigmaPoints(xPred, predictedCovariance)
        val zSigma = DoubleArray(2 * n + 1)

        // Measurement Model h(x) = x[0] (Glucose)
        for (i in 0 until 2 * n + 1) zSigma[i] = sigmaPoints[i][0]

        // Predicted Measurement Mean
        var zPred = 0.0
        for (i in 0 until 2 * n + 1) zPred += Wm[i] * zSigma[i]

        // Measurement Variance
        var innovationVariance = 0.0
        for (i in 0 until 2 * n + 1) {
            val dz = zSigma[i] - zPred
            innovationVariance += Wc[i] * dz * dz
        }
        innovationVariance += measurementNoiseVariance
        val innovationVarianceSafe = max(innovationVariance, 1e-6)

        // Cross covariance (state × measurement)
        val crossCovariance = DoubleArray(n)
        for (i in 0 until 2 * n + 1) {
            val dx0 = sigmaPoints[i][0] - xPred[0]
            val dx1 = sigmaPoints[i][1] - xPred[1]
            val dz = zSigma[i] - zPred
            crossCovariance[0] += Wc[i] * dx0 * dz
            crossCovariance[1] += Wc[i] * dx1 * dz
        }

        // Kalman Gain
        val K = DoubleArray(n)
        K[0] = crossCovariance[0] / innovationVarianceSafe
        K[1] = crossCovariance[1] / innovationVarianceSafe

        // Update State
        val innovation = z - zPred
        x[0] = xPred[0] + K[0] * innovation
        x[1] = xPred[1] + K[1] * innovation
        
        x[1] = x[1].coerceIn(-5.0, 5.0) // Clamp rate physics

        // Update Covariance
        covariance[0] = predictedCovariance[0] - K[0] * innovationVarianceSafe * K[0]
        covariance[1] = predictedCovariance[1] - K[0] * innovationVarianceSafe * K[1]
        covariance[2] = predictedCovariance[2] - K[1] * innovationVarianceSafe * K[0]
        covariance[3] = predictedCovariance[3] - K[1] * innovationVarianceSafe * K[1]
        
        covariance[0] = max(covariance[0], 0.1)
        covariance[3] = max(covariance[3], 0.001)
    }

    private fun generateSigmaPoints(x: DoubleArray, covariance: DoubleArray): Array<DoubleArray> {
        val sigmaPoints = Array(2 * n + 1) { DoubleArray(n) }
        val sqrtP = matrixSqrt2x2(covariance)
        
        sigmaPoints[0][0] = x[0]; sigmaPoints[0][1] = x[1]

        for (i in 0 until n) {
            sigmaPoints[i + 1][0] = x[0] + gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1][1] = x[1] + gamma * sqrtP[i * 2 + 1]
            sigmaPoints[i + 1 + n][0] = x[0] - gamma * sqrtP[i * 2 + 0]
            sigmaPoints[i + 1 + n][1] = x[1] - gamma * sqrtP[i * 2 + 1]
        }
        return sigmaPoints
    }

    private fun matrixSqrt2x2(covariance: DoubleArray): DoubleArray {
        val a = covariance[0]
        val b = (covariance[1] + covariance[2]) / 2.0
        val d = covariance[3]
        
        val l11 = sqrt(max(a, 1e-9))
        val l21 = b / l11
        val discriminant = d - l21 * l21
        
        val l22 = if (discriminant < 0) sqrt(max(d, 1e-9)) else sqrt(discriminant)
        
        return doubleArrayOf(l11, l21, 0.0, l22)
    }

    // ============================================================
    // ADAPTATION AND UTILS
    // ============================================================

    private fun adaptMeasurementNoise(currentR: Double, innovations: ArrayDeque<Double>, rawInnovationsSquared: ArrayDeque<Double>): Double {
        if (innovations.size < 8) return currentR
        val avgInnovSq = med(innovations)
        
        // Stability Clamp
        if (innovations.any { it > 9.0 }) return currentR.coerceIn(R_MIN, R_MAX)

        var newR = currentR
        if (avgInnovSq >= 1.1 || avgInnovSq <= 0.9) {
            newR = currentR + 0.06 * (med(rawInnovationsSquared) - currentR)
        }
        return newR.coerceIn(R_MIN, R_MAX)
    }

    private fun med(list: Collection<Double>): Double {
       val sorted = list.sorted()
       return if (sorted.size % 2 == 0) (sorted[sorted.size/2] + sorted[(sorted.size-1)/2])/2.0 else sorted[sorted.size/2]
    }

    private fun trackInnovation(innovation: Double, innovationVariance: Double) {
        val normalizedSq = (innovation * innovation) / innovationVariance
        val rawSq = innovation * innovation
        innovations.addFirst(normalizedSq)
        rawInnovationVariance.addFirst(rawSq)
        if (innovations.size > innovationWindow) innovations.removeLast()
        if (rawInnovationVariance.size > innovationWindow) rawInnovationVariance.removeLast()
    }

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

    private fun copyRawToSmoothed(data: MutableList<InMemoryGlucoseValue>) {
       data.forEach { 
           it.smoothed = it.value
           it.trendArrow = TrendArrow.NONE
       }
    }

    private fun sanitizeOutput(data: MutableList<InMemoryGlucoseValue>) {
        data.forEach { gv ->
            val smoothed = gv.smoothed
            if (smoothed == null || !smoothed.isFinite()) {
                gv.smoothed = gv.value.coerceIn(MIN_VALID_BG, MAX_VALID_BG)
                gv.trendArrow = TrendArrow.FLAT
                return@forEach
            }
            gv.smoothed = smoothed.coerceIn(MIN_VALID_BG, MAX_VALID_BG)
        }
    }

    // ============================================================
    // PERSISTENCE & SENSOR MANAGEMENT
    // ============================================================
    // Simplified for robustness

    private fun loadPersistedParameters() {
        try {
            learnedR = preferences.get(DoubleNonKey.UkfLearnedR)
            lastProcessedTimestamp = preferences.get(LongNonKey.UkfLastProcessedTimestamp)
            lastSensorChangeTimestamp = preferences.get(LongNonKey.UkfSensorChangeTimestamp)
        } catch (e: Exception) {
            learnedR = DoubleNonKey.UkfLearnedR.defaultValue
        }
    }

    private fun savePersistedParameters() {
        try {
            preferences.put(DoubleNonKey.UkfLearnedR, learnedR)
            preferences.put(LongNonKey.UkfLastProcessedTimestamp, lastProcessedTimestamp)
            preferences.put(LongNonKey.UkfSensorChangeTimestamp, lastSensorChangeTimestamp)
        } catch (e: Exception) { }
    }

    private fun shouldResetLearning(currentTimestamp: Long): Boolean {
        if (resetRequested.getAndSet(false)) return true
        if (lastProcessedTimestamp == 0L) return true
        val diffMinutes = (currentTimestamp - lastProcessedTimestamp) / 60000.0
        if (diffMinutes < 0) return false
        if (diffMinutes > 1440) return true
        return false
    }

    private fun resetLearning() {
        learnedR = DoubleNonKey.UkfLearnedR.defaultValue
        innovations.clear()
        rawInnovationVariance.clear()
        aapsLogger.info(LTag.GLUCOSE, "HybridSmoothing: Learning Reset. R=$learnedR")
        savePersistedParameters()
    }

    private suspend fun loadInitialSensorChangeFromDb() {
        val events = persistenceLayer.getTherapyEventDataFromTime(System.currentTimeMillis() - 30L * 24 * 3600 * 1000, false)
        val latest = events.asSequence().filter { it.type == TE.Type.SENSOR_CHANGE }.maxByOrNull { it.timestamp }
        if (latest != null && latest.timestamp > lastSensorChangeTimestamp) {
            lastSensorChangeTimestamp = latest.timestamp
            resetRequested.set(true)
        }
    }

    private fun checkForSensorChange() {
        sensorBackfillJob?.cancel()
        sensorBackfillJob = smoothingScope.launch {
            if (!config.awaitInitialized(30_000L)) return@launch
            runCatching { loadInitialSensorChangeFromDb() }
                .onFailure { t -> aapsLogger.error(LTag.GLUCOSE, "AdaptiveSmoothing: loadLastSensorChange error", t) }
        }
    }

    private companion object {
        const val SENSOR_CHANGE_DEBOUNCE_SECONDS: Long = 10L
        const val MIN_VALID_BG: Double = 39.0
        const val MAX_VALID_BG: Double = 500.0
    }
}
