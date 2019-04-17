package info.nightscout.androidaps.plugins.general.versionChecker

import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*

/**
 * Usually we would have a class here.
 * Instead of having a class we can use an object directly inherited from PluginBase.
 * This is a lazy loading singleton only loaded when actually used.
 * */

object VersionCheckerPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.versionChecker)), ConstraintsInterface {

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return if (isVeryOldVersion())
            Constraint(false)
        else
            value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        return if (isOldVersion())
            Constraint(0.toDouble())
        else
            maxIob
    }

    private fun isOldVersion(): Boolean {
        return true
    }

    private fun isVeryOldVersion(): Boolean {
        return true
    }

}
