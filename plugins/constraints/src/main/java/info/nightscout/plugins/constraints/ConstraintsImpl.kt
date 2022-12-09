package info.nightscout.plugins.constraints

import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstraintsImpl @Inject constructor(private val activePlugin: ActivePlugin) : Constraints {

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isLoopInvocationAllowed(value)
        }
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isClosedLoopAllowed(value)
        }
        return value
    }

    override fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isLgsAllowed(value)
        }
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isAutosensModeEnabled(value)
        }
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isSMBModeEnabled(value)
        }
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isUAMEnabled(value)
        }
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isAdvancedFilteringEnabled(value)
        }
        return value
    }

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isSuperBolusEnabled(value)
        }
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.applyBasalConstraints(absoluteRate, profile)
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as Constraints
            if (!p.isEnabled()) continue
            constrain.applyBasalPercentConstraints(percentRate, profile)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as Constraints
            if (!p.isEnabled()) continue
            constrain.applyBolusConstraints(insulin)
        }
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as Constraints
            if (!p.isEnabled()) continue
            constrain.applyExtendedBolusConstraints(insulin)
        }
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as Constraints
            if (!p.isEnabled()) continue
            constrain.applyCarbsConstraints(carbs)
        }
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as Constraints
            if (!p.isEnabled()) continue
            constrain.applyMaxIOBConstraints(maxIob)
        }
        return maxIob
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = activePlugin.getSpecificPluginsListByInterface(Constraints::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as Constraints
            if (!p.isEnabled()) continue
            constraint.isAutomationEnabled(value)
        }
        return value
    }
}