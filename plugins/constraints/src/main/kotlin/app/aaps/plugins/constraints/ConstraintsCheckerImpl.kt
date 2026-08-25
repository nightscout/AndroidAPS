package app.aaps.plugins.constraints

import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.PumpPluginConstraints
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.constraints.ConstraintObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstraintsCheckerImpl @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val aapsLogger: AAPSLogger,
    private val ch: ConcentrationHelper,
    private val rh: ResourceHelper
) : ConstraintsChecker {

    override fun isLoopInvocationAllowed(): Constraint<Boolean> = isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isLoopInvocationAllowed(value)
        }
        return value
    }

    override suspend fun isClosedLoopAllowed(): Constraint<Boolean> = isClosedLoopAllowed(ConstraintObject(true, aapsLogger))

    override suspend fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isClosedLoopAllowed(value)
        }
        return value
    }

    override fun isLgsForced(): Constraint<Boolean> = isLgsForced(ConstraintObject(false, aapsLogger))

    override fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isLgsForced(value)
        }
        return value
    }

    override fun isAutosensModeEnabled(): Constraint<Boolean> = isAutosensModeEnabled(ConstraintObject(true, aapsLogger))

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isAutosensModeEnabled(value)
        }
        return value
    }

    override suspend fun isSMBModeEnabled(): Constraint<Boolean> = isSMBModeEnabled(ConstraintObject(true, aapsLogger))

    override suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isSMBModeEnabled(value)
        }
        return value
    }

    override fun isUAMEnabled(): Constraint<Boolean> = isUAMEnabled(ConstraintObject(true, aapsLogger))

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isUAMEnabled(value)
        }
        return value
    }

    override suspend fun isAdvancedFilteringEnabled(): Constraint<Boolean> = isAdvancedFilteringEnabled(ConstraintObject(true, aapsLogger))

    override suspend fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isAdvancedFilteringEnabled(value)
        }
        return value
    }

    override fun isSuperBolusEnabled(): Constraint<Boolean> = isSuperBolusEnabled(ConstraintObject(true, aapsLogger))

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isSuperBolusEnabled(value)
        }
        return value
    }

    override fun isAutomationEnabled(): Constraint<Boolean> = isAutomationEnabled(ConstraintObject(true, aapsLogger))

    override fun isConcentrationEnabled(): Constraint<Boolean> = isConcentrationEnabled(ConstraintObject(true, aapsLogger))

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.applyBasalConstraints(absoluteRate, profile)
        }
        // Fold the active pump's own cU rate cap into the IU result. Single cU<->IU conversion point (ch): the pump
        // reports cU, this scan is IU. Being in the IU scan (not the absolute-delivery boundary) makes the cap apply
        // to BOTH absolute and percent pumps; a pump without a cap doesn't implement the interface (no-op).
        (activePlugin.activePumpInternal as? PumpPluginConstraints)?.let { pump ->
            val capIu = ch.fromPump(pump.applyBasalConstraints(ch.toPumpRate(absoluteRate.value())))
            absoluteRate.setIfSmaller(capIu, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, capIu, rh.gs(app.aaps.core.ui.R.string.pumplimit)), pump)
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as PluginConstraints
            if (!p.isEnabled()) continue
            constrain.applyBasalPercentConstraints(percentRate, profile)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as PluginConstraints
            if (!p.isEnabled()) continue
            constrain.applyBolusConstraints(insulin)
        }
        // Fold the active pump's own cU bolus cap into the IU result (single cU<->IU conversion point via ch).
        (activePlugin.activePumpInternal as? PumpPluginConstraints)?.let { pump ->
            val capIu = ch.fromPump(pump.applyBolusConstraints(ch.toPump(insulin.value())))
            insulin.setIfSmaller(capIu, rh.gs(app.aaps.core.ui.R.string.limitingbolus, capIu, rh.gs(app.aaps.core.ui.R.string.pumplimit)), pump)
        }
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as PluginConstraints
            if (!p.isEnabled()) continue
            constrain.applyExtendedBolusConstraints(insulin)
        }
        // Fold the active pump's own cU extended-bolus cap into the IU result (single conversion point via ch).
        (activePlugin.activePumpInternal as? PumpPluginConstraints)?.let { pump ->
            val capIu = ch.fromPump(pump.applyExtendedBolusConstraints(ch.toPump(insulin.value())))
            insulin.setIfSmaller(capIu, rh.gs(app.aaps.core.ui.R.string.limitingbolus, capIu, rh.gs(app.aaps.core.ui.R.string.pumplimit)), pump)
        }
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as PluginConstraints
            if (!p.isEnabled()) continue
            constrain.applyCarbsConstraints(carbs)
        }
        return carbs
    }

    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as PluginConstraints
            if (!p.isEnabled()) continue
            constrain.applyMaxIOBConstraints(maxIob)
        }
        return maxIob
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isAutomationEnabled(value)
        }
        return value
    }

    override fun isConcentrationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(PluginConstraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as PluginConstraints
            if (!p.isEnabled()) continue
            constraint.isConcentrationEnabled(value)
        }
        return value
    }

    /*
     * Determine max values by walking through all constraints
     */

    override fun getMaxBasalAllowed(profile: Profile): Constraint<Double> =
        applyBasalConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger), profile)

    override fun getMaxBasalPercentAllowed(profile: Profile): Constraint<Int> =
        applyBasalPercentConstraints(ConstraintObject(Int.MAX_VALUE, aapsLogger), profile)

    override fun getMaxBolusAllowed(): Constraint<Double> =
        applyBolusConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger))

    override fun getMaxExtendedBolusAllowed(): Constraint<Double> =
        applyExtendedBolusConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger))

    override fun getMaxCarbsAllowed(): Constraint<Int> =
        applyCarbsConstraints(ConstraintObject(HardLimits.MAX_CARBS, aapsLogger))

    override suspend fun getMaxIOBAllowed(): Constraint<Double> =
        applyMaxIOBConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger))
}
