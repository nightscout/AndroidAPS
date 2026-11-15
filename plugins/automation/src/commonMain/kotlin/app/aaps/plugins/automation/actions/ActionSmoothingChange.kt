package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerSelectedSmoothingNotActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionSmoothingChange(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    // Only to build the Trigger precondition below.
    triggerDeps: TriggerDeps
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    // Holds the pluginId of the selected smoothing plugin
    var smoothingPlugin: InputDropdownMenu = InputDropdownMenu()

    // Skip the whole automation (no log entry) once the selected smoothing is
    // already active — the trigger stays true and would re-fire every cycle.
    override var precondition: Trigger? = TriggerSelectedSmoothingNotActive(triggerDeps) { smoothingPlugin.value }

    /** All installed smoothing plugins, same list as Configuration → Smoothing. */
    fun smoothingOptions(): List<PluginBase> = activePlugin.getSpecificPluginsList(PluginType.SMOOTHING)

    override fun friendlyName(): TextRef = AutomationStrings.change_smoothing
    override fun shortDescription(): String =
        if (smoothingPlugin.value.isEmpty()) rh.gs(AutomationStrings.change_smoothing)
        else rh.gs(AutomationStrings.change_smoothing_to, resolvePlugin()?.name ?: smoothingPlugin.value)

    override fun composeIcon() = Icons.Default.Timeline

    override suspend fun doAction(): PumpEnactResult {
        val plugin = resolvePlugin()
        if (plugin == null) {
            aapsLogger.error(LTag.AUTOMATION, "Smoothing plugin not found: ${smoothingPlugin.value}")
            return pumpEnactResultProvider().success(false).comment(CoreUiStrings.error)
        }
        // The trigger can stay true and fire again. Do nothing when the plugin is
        // already active — a real switch would restart the full IobCob calculation.
        if (plugin.isEnabled(PluginType.SMOOTHING)) {
            aapsLogger.debug(LTag.AUTOMATION, "Smoothing already set to ${plugin.name}")
            return pumpEnactResultProvider().success(true).comment(AutomationStrings.alreadyset)
        }
        configBuilder.performPluginSwitch(plugin, true, PluginType.SMOOTHING)
        return pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject { put("smoothingPlugin", smoothingPlugin.value) }
        return buildJsonObject {
            put("type", this@ActionSmoothingChange::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        smoothingPlugin.value = o.lenientString("smoothingPlugin", "")
        return this
    }

    override fun isValid(): Boolean = resolvePlugin() != null

    private fun resolvePlugin(): PluginBase? =
        activePlugin.getSpecificPluginsList(PluginType.SMOOTHING).firstOrNull { it.pluginId == smoothingPlugin.value }
}
