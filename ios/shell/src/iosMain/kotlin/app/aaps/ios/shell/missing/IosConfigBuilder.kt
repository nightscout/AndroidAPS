package app.aaps.ios.shell.missing

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Placeholder. Plugin switching is accepted and does nothing, so the settings screens can be opened
 * and read without a switch appearing to take effect when it has not.
 *
 * `ConfigBuilderImpl` is 320 lines and only partly Android: the plugin bookkeeping is ordinary
 * logic, but [exitApp] restarts the process through `AlarmManager` and a `PendingIntent`, which has
 * no iOS equivalent at all - an app may not relaunch itself, and Apple treats a self-terminating app
 * as a crash. So this one will always need a real iOS half, even after the shared part moves.
 *
 * [requestPluginSwitch] returns null, which the interface reads as "no confirmation needed". That is
 * the honest answer here rather than a convenient one: nothing is switched, so there is nothing for
 * a user to confirm.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosConfigBuilder @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ConfigBuilder {

    override val syncedSelectionTypes: List<PluginType> = emptyList()

    private val _activeSelectionChanges = MutableSharedFlow<Unit>()
    override val activeSelectionChanges: Flow<Unit> = _activeSelectionChanges.asSharedFlow()

    override fun initialize() = aapsLogger.notOnIosYet("ConfigBuilder.initialize")

    override fun storeSettings(from: String) = aapsLogger.notOnIosYet("ConfigBuilder.storeSettings from $from")

    override fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType) =
        aapsLogger.notOnIosYet("ConfigBuilder.performPluginSwitch")

    override fun requestPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType): String? {
        aapsLogger.notOnIosYet("ConfigBuilder.requestPluginSwitch")
        return null
    }

    override fun confirmPumpPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType) =
        aapsLogger.notOnIosYet("ConfigBuilder.confirmPumpPluginSwitch")

    override fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType) =
        aapsLogger.notOnIosYet("ConfigBuilder.processOnEnabledCategoryChanged")

    /** iOS has no way to do this: an app may not restart itself, and quitting reads as a crash. */
    override fun exitApp(from: String, source: Sources, launchAgain: Boolean) =
        aapsLogger.notOnIosYet("ConfigBuilder.exitApp from $from")
}
