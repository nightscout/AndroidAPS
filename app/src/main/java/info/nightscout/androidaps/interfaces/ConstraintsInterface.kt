package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Profile

/**
 * Created by mike on 15.06.2016.
 */
interface ConstraintsInterface {

    @JvmDefault
    fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    @JvmDefault
    fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        return absoluteRate
    }

    @JvmDefault
    fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        return percentRate
    }

    @JvmDefault
    fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return insulin
    }

    @JvmDefault
    fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return insulin
    }

    @JvmDefault
    fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        return carbs
    }

    @JvmDefault
    fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        return maxIob
    }

    @JvmDefault
    fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }
}