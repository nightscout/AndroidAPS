package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate

/**
 * A pump driver's own delivery-amount limits (max bolus / max temp basal), expressed in pump units
 * (cU), NOT IU. The active pump implements this IN ADDITION to [PluginConstraints].
 *
 * Concentration (U200 -> `cU = IU / 2`, diluted U20 -> `cU = IU * 5`) is converted in exactly one
 * place: `ConstraintsChecker` folds these cU limits into its IU constraint scan (`getMaxBolusAllowed`
 * / `getMaxBasalAllowed`), recalculating cU->IU via `ConcentrationHelper`. Because that scan is the
 * system-wide IU limit collection used for BOTH absolute and percent delivery, this works for every
 * pump style - unlike a cap applied only at the absolute-delivery boundary, which a percent-style
 * pump (e.g. DanaR) would never hit.
 *
 * The [PumpInsulin] / [PumpRate] parameters make the unit explicit and impossible to misfeed with a
 * bare IU `Double`: the pump reports its native cU limits and never deals with concentration itself.
 *
 * A pump keeps implementing [PluginConstraints] for its unit-less / IU constraints (percent basal,
 * loop allowed, SMB, carbs, ...). The native pulse/step quantization of the delivered amount is a
 * separate concern, handled by `PumpWithConcentration` at IU->cU conversion time.
 */
interface PumpPluginConstraints {

    /** Cap a bolus to the pump's delivery limit, in pump units (cU). */
    fun applyBolusConstraints(insulin: PumpInsulin): PumpInsulin = insulin

    /** Cap an extended bolus to the pump's delivery limit, in pump units (cU). */
    fun applyExtendedBolusConstraints(insulin: PumpInsulin): PumpInsulin = insulin

    /** Cap an absolute temp basal to the pump's delivery limit, in pump units (cU/h). */
    fun applyBasalConstraints(absoluteRate: PumpRate): PumpRate = absoluteRate
}
