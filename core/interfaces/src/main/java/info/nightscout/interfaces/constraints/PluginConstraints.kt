package info.nightscout.interfaces.constraints

import info.nightscout.interfaces.profile.Profile

/**
 * PluginConstraints interface
 *
 * Allows to every plugin implement own constraints
 */
interface PluginConstraints {

    fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isDynIsfModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
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