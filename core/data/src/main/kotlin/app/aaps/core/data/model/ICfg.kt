package app.aaps.core.data.model

import app.aaps.core.data.iob.Iob
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Insulin configuration holds info about insulin
 */
data class ICfg(
    /**
     * Insulin name
     */
    var insulinLabel: String,
    /**
     * Aka DIA before in milliseconds
     */
    var insulinEndTime: Long,
    /**
     * Peak time from start in milliseconds
     */
    var insulinPeakTime: Long,
    /**
     * Insulin concentration (0.2 for U20, 2.0 for U200 insulin)
     */
    var concentration: Double = 1.0
) {

    constructor(insulinLabel: String, peak: Int, dia: Double, concentration: Double)
        : this(insulinLabel = insulinLabel, insulinEndTime = (dia * 3600 * 1000).toLong(), insulinPeakTime = (peak * 60000).toLong(), concentration = concentration)
    /**
    * Used in InsulinPlugin (insulin editor)
    */
    fun isEqual(iCfg: ICfg?): Boolean {
        iCfg?.let { iCfg ->
            if (insulinLabel != iCfg.insulinLabel)
                return false
            if (insulinEndTime != iCfg.insulinEndTime)
                return false
            if (insulinPeakTime != iCfg.insulinPeakTime)
                return false
            if (concentration != iCfg.concentration)
                return false
            return true
        }
        return false
    }
    /**
     * DIA (insulinEndTime) in hours rounded to 1 decimal place
     */
    val dia: Double
        get() = (insulinEndTime / 3600.0 / 100.0).roundToInt() / 10.0

    /**
     * Peak time in minutes
     */
    val peak: Int
        get() = (insulinPeakTime / 60000).toInt()

    /**
     * Set insulinEndTime aka DIA
     * @param hours duration in hours
     */
    fun setDia(hours: Double) {
        insulinEndTime = (hours * 3600 * 1000).toLong()
    }

    /**
     * Set insulinPeakTime aka peak
     * @param minutes peak tme in minutes
     */
    fun setPeak(minutes: Int) {
        insulinPeakTime = (minutes * 60000).toLong()
    }

    /**
     * insulinNickname is only used in insulin editor
     */
    var insulinNickname: String = ""

    /**
     * deepClone is only used in insulin editor
     */
    fun deepClone(): ICfg = ICfg(insulinLabel, insulinEndTime, insulinPeakTime, concentration).also { it.insulinNickname = insulinNickname }

    fun iobCalcForTreatment(bolus: BS, time: Long): Iob {
        assert(insulinEndTime != 0L)
        assert(insulinPeakTime != 0L)
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            // Defensive clamp: the bilinear IOB model is only well-defined for 0 < tp < td/2. Corrupt
            // iCfg (the v33 migration sentinel -1 before its repair, or a malformed NS-imported insulin
            // config) would otherwise divide by zero, produce a negative tau, or — when td <= 0 — make
            // the `t < td` gate never fire and silently contribute ZERO IOB, all of which mislead the
            // loop into overdelivery. These bounds are MATH-validity floors only, NOT the medical limits
            // (which are enforced upstream): legitimate peaks below HardLimits.MIN_PEAK (e.g. 30 min) and
            // any dia >= MIN_DIA are preserved unchanged; only degenerate values are sanitized.
            val td = (dia * 60).coerceAtLeast(MIN_DIA_MINUTES)
            val tp = peak.toDouble().coerceIn(MIN_PEAK_MINUTES, td / 2.0 - 1.0)
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - s * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1))
            }
        }
        return result
    }

    companion object {
        // Math-validity floors for iobCalcForTreatment. They only engage for corrupt/degenerate iCfg
        // and are no-ops for real configs; they are NOT the medical HardLimits.
        private const val MIN_DIA_MINUTES = 300.0 // 5 h (mirrors HardLimits.MIN_DIA); floors corrupt/sentinel DIA <= 0
        private const val MIN_PEAK_MINUTES = 1.0  // just keeps tp > 0; real peaks (incl. sub-MIN_PEAK like 30 min) pass through
    }
}