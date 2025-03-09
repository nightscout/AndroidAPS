package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.plugins.constraints.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerPlugin @Inject constructor(
    private val sp: SP,
    rh: ResourceHelper,
    private val versionCheckerUtils: VersionCheckerUtils,
    aapsLogger: AAPSLogger,
    private val config: Config,
    private val dateUtil: DateUtil,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.version_checker),
    aapsLogger, rh
), PluginConstraints {

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        versionCheckerUtils.triggerCheckVersion()
        val endDate = sp.getLong(rh.gs(app.aaps.core.utils.R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        return if (endDate != 0L && dateUtil.now() > endDate)
            maxIob.set(0.0, rh.gs(R.string.application_expired), this)
        else
            maxIob
    }

}
