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
    var concentration: Double = 1.0,
    /**
     * True for inhaled insulin (e.g. Afrezza).
     *
     * Authored in the insulin editor and stored - deliberately NOT re-derived from
     * [insulinPeakTime]. Only the factory-default Afrezza peak (15 min) matches a template exactly,
     * so deriving it drops the inhaled identity for any peak the user picks inside the valid
     * 10-30 min range, taking the inhaled DIA limits and the Afrezza dialog's insulin lookup with it.
     *
     * Where no stored flag exists (a row read back from the database, legacy catalogue JSON, a
     * Nightscout payload written by an older build) it is reconstructed from the peak via
     * `InsulinType.isInhaledPeak`, which is unambiguous because the two peak ranges are disjoint.
     */
    var isInhaled: Boolean = false
) {

    constructor(insulinLabel: String, peak: Int, dia: Double, concentration: Double, isInhaled: Boolean = false)
        : this(
        insulinLabel = insulinLabel, insulinEndTime = (dia * 3600 * 1000).toLong(), insulinPeakTime = (peak * 60000).toLong(),
        concentration = concentration, isInhaled = isInhaled
    )
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
            if (isInhaled != iCfg.isInhaled)
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
     * False for the `insulinEndTime = -1` sentinel the DB v33 migration writes into rows that predate ICfg
     * (and for anything else non-positive). Such a record is not an insulin: [dia] rounds to `0.0`, which is
     * outside the hard limits, so an APS run that accepts it aborts every cycle instead of dosing.
     *
     * Treat it as "no insulin" — the same as absent — so callers fall through to their normal
     * ask-or-refuse path rather than propagating a degenerate curve.
     */
    val isUsable: Boolean
        get() = insulinEndTime > 0 && insulinPeakTime > 0

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
    fun deepClone(): ICfg = ICfg(insulinLabel, insulinEndTime, insulinPeakTime, concentration, isInhaled).also { it.insulinNickname = insulinNickname }

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
            // (which are enforced upstream): legitimate peaks below HardLimits.LIMIT_PEAK (e.g. 30 min) and
            // any dia >= MIN_DIA are preserved unchanged; only degenerate values are sanitized.
            //
            // Two different floors, not one: a genuinely corrupt/sentinel config (insulinEndTime <= 0)
            // must still floor to MIN_DIA_MINUTES_SENTINEL (5h) so a real outstanding bolus is never
            // silently zeroed. A real, even if short, DIA (e.g. Afrezza's 1.0-2.0h) only needs the much
            // lower MIN_DIA_MINUTES floor to guard against literal zero/negative math inputs - flooring
            // it to 5h would wrongly stretch inhaled insulin's fast IOB taper.
            val diaFloorMinutes = if (insulinEndTime > 0) MIN_DIA_MINUTES else MIN_DIA_MINUTES_SENTINEL
            val td = (dia * 60).coerceAtLeast(diaFloorMinutes)
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
        private const val MIN_DIA_MINUTES = 30.0 // 0.5 h; guards a real (even short inhaled, e.g. Afrezza 1.0-2.0 h) DIA against literal zero/negative math inputs
        private const val MIN_DIA_MINUTES_SENTINEL = 300.0 // 5 h (mirrors HardLimits.MIN_DIA); floors a corrupt/sentinel config (insulinEndTime <= 0) so it never silently zeros a real bolus's IOB
        private const val MIN_PEAK_MINUTES = 1.0  // just keeps tp > 0; real peaks (incl. sub-MIN_PEAK like 30 min) pass through
    }
}