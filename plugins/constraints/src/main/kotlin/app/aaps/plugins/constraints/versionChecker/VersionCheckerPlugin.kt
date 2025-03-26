package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.LongComposedKey
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
    private val versionCheckerUtils: VersionCheckerUtils,
    private val config: Config,
    private val dateUtil: DateUtil
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
        versionCheckerUtils.triggerCheckVersion()
        val endDate = preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)
        return if (endDate != 0L && dateUtil.now() > endDate)
            maxIob.set(0.0, rh.gs(R.string.application_expired), this)
        else
            maxIob
    }
}
