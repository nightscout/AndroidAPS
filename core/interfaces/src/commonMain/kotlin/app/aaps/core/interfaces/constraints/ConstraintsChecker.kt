package app.aaps.core.interfaces.constraints

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpInsulin

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
    suspend fun isClosedLoopAllowed(): Constraint<Boolean>
    fun isLgsForced(): Constraint<Boolean>
    fun isAutosensModeEnabled(): Constraint<Boolean>
    suspend fun isSMBModeEnabled(): Constraint<Boolean>
    fun isUAMEnabled(): Constraint<Boolean>
    suspend fun isAdvancedFilteringEnabled(): Constraint<Boolean>
    fun isSuperBolusEnabled(): Constraint<Boolean>
    fun isAutomationEnabled(): Constraint<Boolean>
    fun isConcentrationEnabled(): Constraint<Boolean>

    /*
     * Determine max values by walking through all constraints
     */
    fun getMaxBasalAllowed(profile: Profile): Constraint<Double>
    fun getMaxBasalPercentAllowed(profile: Profile): Constraint<Int>
    fun getMaxBolusAllowed(): Constraint<Double>
    fun getMaxExtendedBolusAllowed(): Constraint<Double>
    fun getMaxCarbsAllowed(): Constraint<Int>
    suspend fun getMaxIOBAllowed(): Constraint<Double>

    /**
     * Limits a prime or fill amount. It is in pump units: it goes to the pump as it is, the insulin
     * concentration does not convert it. [applyBolusConstraints] takes insulin units, so priming has its
     * own function.
     *
     * The max bolus of the constraint plugins is used as a plain number of pump units, not converted by the
     * concentration, as it was before concentrations. The limits of the pump itself (its max, and a min for
     * Insight) are in pump units already and apply as they are. The amount is rounded down to the step of
     * the pump.
     */
    fun applyPrimingConstraints(amount: PumpInsulin): PumpInsulin
}
