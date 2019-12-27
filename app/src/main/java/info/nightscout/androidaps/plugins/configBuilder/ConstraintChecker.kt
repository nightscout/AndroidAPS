package info.nightscout.androidaps.plugins.configBuilder

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstraintChecker @Inject constructor(private val mainApp: MainApp) : ConstraintsInterface {

    init {
        instance = this
    }

    companion object {
        @JvmStatic
        lateinit var instance: ConstraintChecker
    }

    fun isLoopInvocationAllowed(): Constraint<Boolean> =
        isLoopInvocationAllowed(Constraint(true))

    fun isClosedLoopAllowed(): Constraint<Boolean> =
        isClosedLoopAllowed(Constraint(true))

    fun isAutosensModeEnabled(): Constraint<Boolean> =
        isAutosensModeEnabled(Constraint(true))

    fun isAMAModeEnabled(): Constraint<Boolean> =
        isAMAModeEnabled(Constraint(true))

    fun isSMBModeEnabled(): Constraint<Boolean> =
        isSMBModeEnabled(Constraint(true))

    fun isUAMEnabled(): Constraint<Boolean> =
        isUAMEnabled(Constraint(true))

    fun isAdvancedFilteringEnabled(): Constraint<Boolean> =
        isAdvancedFilteringEnabled(Constraint(true))

    fun isSuperBolusEnabled(): Constraint<Boolean> =
        isSuperBolusEnabled(Constraint(true))

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

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isLoopInvocationAllowed(value)
        }
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isClosedLoopAllowed(value)
        }
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isAutosensModeEnabled(value)
        }
        return value
    }

    override fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.isAMAModeEnabled(value)
        }
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isSMBModeEnabled(value)
        }
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isUAMEnabled(value)
        }
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isAdvancedFilteringEnabled(value)
        }
        return value
    }

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.isSuperBolusEnabled(value)
        }
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constraint = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constraint.applyBasalConstraints(absoluteRate, profile)
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.applyBasalPercentConstraints(percentRate, profile)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.applyBolusConstraints(insulin)
        }
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.applyExtendedBolusConstraints(insulin)
        }
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.applyCarbsConstraints(carbs)
        }
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val constraintsPlugins = mainApp.getSpecificPluginsListByInterface(ConstraintsInterface::class.java)
        for (p in constraintsPlugins) {
            val constrain = p as ConstraintsInterface
            if (!p.isEnabled(PluginType.CONSTRAINTS)) continue
            constrain.applyMaxIOBConstraints(maxIob)
        }
        return maxIob
    }
}