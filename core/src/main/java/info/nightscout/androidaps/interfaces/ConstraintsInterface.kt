package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Profile

/**
 * Created by mike on 15.06.2016.
 */
interface ConstraintsInterface {

    @JvmDefault fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
    @JvmDefault fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> = absoluteRate
    @JvmDefault fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> = percentRate
    @JvmDefault fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    @JvmDefault fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> = insulin
    @JvmDefault fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> = carbs
    @JvmDefault fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> = maxIob
    @JvmDefault fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value
}