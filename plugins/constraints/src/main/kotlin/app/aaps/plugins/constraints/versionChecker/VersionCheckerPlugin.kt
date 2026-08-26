package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.di.NotNSClient
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import javax.inject.Inject

// Registers itself into the plugin list. Scoped with Metro's @SingleIn, not javax @Singleton: a
// contributed class is built by the graph generated in `:app`, which has no Dagger interop, so a javax
// scope there is ignored and every read would build a new plugin.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@NotNSClient
@IntKey(810)
@SingleIn(AppScope::class)
class VersionCheckerPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val config: Config,
    private val dateUtil: DateUtil
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(TextRef.AndroidRes(R.string.version_checker)),
    ownPreferences = VersionCheckerLongKey.entries,
    aapsLogger, rh, preferences
), PluginConstraints {

    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        versionCheckerUtils.triggerCheckVersion()
        val endDate = preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME)
        return if (endDate != 0L && dateUtil.now() > endDate)
            maxIob.set(0.0, rh.gs(R.string.application_expired), this)
        else
            maxIob
    }
}
