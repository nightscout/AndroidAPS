package app.aaps.core.data.model

/**
 * Immutable result of a bolus-wizard calculation — the computed dose plus the per-component breakdown a
 * UI displays. A pure `core:data` record (primitives only): no `Profile`/`GlucoseStatus` (those are calc
 * inputs/internals, never read as results), so it can flow up the shared bolus path to any caller —
 * the wizard dialog, the wear preview, the client-control preview.
 *
 * Produced by `BolusWizard.doCalc`; over time the calculation moves behind a `BolusWizardCalculator` that
 * returns this directly. [insulinAfterConstraints] is the safe, constraint-capped dose; [calculatedTotalInsulin]
 * is the pre-cap total.
 */
data class BolusWizardData(
    val timeStamp: Long,
    val carbs: Int,
    val cob: Double,
    // Per-component breakdown (for display / explanation)
    val sens: Double,
    val ic: Double,
    val trend: Double,
    val insulinFromBG: Double,
    val insulinFromCarbs: Double,
    val insulinFromBolusIOB: Double,
    val insulinFromBasalIOB: Double,
    val insulinFromCorrection: Double,
    val insulinFromSuperBolus: Double,
    val insulinFromCOB: Double,
    val insulinFromTrend: Double,
    // Result
    val calculatedTotalInsulin: Double,
    val totalBeforePercentageAdjustment: Double,
    val carbsEquivalent: Double,
    val insulinAfterConstraints: Double,
    val calculatedPercentage: Int,
    val calculatedCorrection: Double,
    val percentageCorrection: Int,
    // Which components were active in this calculation, so a renderer shows the right breakdown lines.
    // Mirrors the flags BCR persists; the explanation text is built per-path from these — never stored here as a string.
    val useBg: Boolean,
    val useCob: Boolean,
    val includeBolusIOB: Boolean,
    val includeBasalIOB: Boolean,
    val useTT: Boolean,
    val useTrend: Boolean,
    val useSuperBolus: Boolean
)
