package info.nightscout.androidaps.interfaces

/**
 * Constraints interface
 *
 * Every function has a param from previous chained call
 * Function can limit the value even more and add another reason of restriction
 *
 * see [info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker]
 * which iterates over all registered plugins with [Constraints] implemented
 *
 * @return updated parameter
 */
interface Constraints {

    fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> = absoluteRate
    fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> = percentRate
    fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> = carbs
    fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> = maxIob
    fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
}