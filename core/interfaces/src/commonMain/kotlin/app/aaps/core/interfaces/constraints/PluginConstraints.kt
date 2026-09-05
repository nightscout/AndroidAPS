package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.profile.Profile

/**
 * PluginConstraints interface
 *
 * Allows to every plugin implement own constraints
 */
interface PluginConstraints {

    fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    suspend fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    suspend fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> = absoluteRate
    fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> = percentRate
    fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> = carbs
    suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> = maxIob
    fun isConcentrationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
}
