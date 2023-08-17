package info.nightscout.interfaces.constraints

import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.profile.Profile

/**
 * Constraints interface
 *
 * Every function has a param from previous chained call
 * Function can limit the value even more and add another reason of restriction
 *
 * see [info.nightscout.implementation.constraints.ConstraintsImpl]
 * which iterates over all registered plugins with [Constraints] implemented
 *
 * @return updated parameter
 */
interface Constraints {

    fun isLoopInvocationAllowed(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isClosedLoopAllowed(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isLgsAllowed(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isAutosensModeEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isSMBModeEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isDynIsfModeEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isUAMEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isAdvancedFilteringEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isSuperBolusEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun isAutomationEnabled(value: Constraint<Boolean> = Constraint(true)): Constraint<Boolean> = value
    fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> = absoluteRate
    fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> = percentRate
    fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> = carbs
    fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> = maxIob

    /*
     * Determine max values by walking through all constraints
     */
    fun getMaxBasalAllowed(profile: Profile): Constraint<Double> =
        applyBasalConstraints(Constraint(Constants.REALLYHIGHBASALRATE), profile)

    fun getMaxBasalPercentAllowed(profile: Profile): Constraint<Int> =
        applyBasalPercentConstraints(Constraint(Constants.REALLYHIGHPERCENTBASALRATE), profile)

    fun getMaxBolusAllowed(): Constraint<Double> =
        applyBolusConstraints(Constraint(Constants.REALLYHIGHBOLUS))

    fun getMaxExtendedBolusAllowed(): Constraint<Double> =
        applyExtendedBolusConstraints(Constraint(Constants.REALLYHIGHBOLUS))

    fun getMaxCarbsAllowed(): Constraint<Int> =
        applyCarbsConstraints(Constraint(Constants.REALLYHIGHCARBS))

    fun getMaxIOBAllowed(): Constraint<Double> =
        applyMaxIOBConstraints(Constraint(Constants.REALLYHIGHIOB))


}