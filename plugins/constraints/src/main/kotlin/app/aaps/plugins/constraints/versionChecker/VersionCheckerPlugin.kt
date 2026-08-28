package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.version_checker),
    ownPreferences = listOf(VersionCheckerLongKey::class.java),
    aapsLogger, rh, preferences
), PluginConstraints {

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        // SHTF eternal build: version-expiry disabled intentionally. See docs/SHTF_LOOP_RESILIENCE_PLAN.md
        // Stock behavior: when a stored expiry date (bundled definition.json or Firebase Remote Config)
        // for the running version has passed, maxIOB is forced to 0, disabling closed-loop dosing.
        // This build must never lose looping to version age, so the constraint is left untouched.
        versionCheckerUtils.triggerCheckVersion()
        return maxIob
    }
}
