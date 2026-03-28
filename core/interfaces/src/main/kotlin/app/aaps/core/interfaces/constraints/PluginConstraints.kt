package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.profile.Profile

/**
 * Safety constraint interface that any plugin can implement.
 *
 * The constraint system is a **chain-of-responsibility** pattern: every enabled plugin
 * implementing this interface gets a chance to restrict values before they are applied.
 * The [ConstraintsChecker] iterates through all active constraint plugins and applies
 * the most restrictive value.
 *
 * ## How It Works
 * Each method receives a [Constraint] wrapper containing the current value and accumulated
 * reasons. The plugin can call `value.setIfSmaller()` or `value.set(false, reason, this)`
 * to tighten the constraint. The original or a more restrictive value is returned.
 *
 * ## Constraint Categories
 * - **Boolean constraints**: Control whether features are allowed (loop, SMB, closed loop, etc.)
 * - **Value constraints**: Limit numerical values (max basal, max bolus, max IOB, max carbs)
 *
 * ## Default Behavior
 * All methods have default implementations that pass through the value unchanged.
 * Plugins only override the constraints they need to enforce.
 *
 * @see Constraint
 * @see app.aaps.core.interfaces.constraints.ConstraintsChecker
 */
interface PluginConstraints {

    fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> = absoluteRate
    fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> = percentRate
    fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> = carbs
    fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> = maxIob
}