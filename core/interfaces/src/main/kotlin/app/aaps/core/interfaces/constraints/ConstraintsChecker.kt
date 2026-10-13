package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.profile.Profile

/**
 * Constraints interface
 *
 * Every function has a param from previous chained call
 * Function can limit the value even more and add another reason of restriction
 *
 * see [app.aaps.plugins.constraints.ConstraintsCheckerImpl]
 * which iterates over all registered plugins with [ConstraintsChecker] implemented
 */
interface ConstraintsChecker : PluginConstraints {

    fun isLoopInvocationAllowed(): Constraint<Boolean>
    fun isClosedLoopAllowed(): Constraint<Boolean>
    fun isLgsForced(): Constraint<Boolean>
    fun isAutosensModeEnabled(): Constraint<Boolean>
    fun isSMBModeEnabled(): Constraint<Boolean>
    fun isUAMEnabled(): Constraint<Boolean>
    fun isAdvancedFilteringEnabled(): Constraint<Boolean>
    fun isSuperBolusEnabled(): Constraint<Boolean>
    fun isAutomationEnabled(): Constraint<Boolean>

    /*
     * Determine max values by walking through all constraints
     */
    fun getMaxBasalAllowed(profile: Profile): Constraint<Double>
    fun getMaxBasalPercentAllowed(profile: Profile): Constraint<Int>
    fun getMaxBolusAllowed(): Constraint<Double>
    fun getMaxExtendedBolusAllowed(): Constraint<Double>
    fun getMaxCarbsAllowed(): Constraint<Int>
    fun getMaxIOBAllowed(): Constraint<Double>
}