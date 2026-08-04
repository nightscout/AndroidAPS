package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerSelectedSmoothingNotActive
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionSmoothingChange(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var configBuilder: ConfigBuilder

    // Holds the pluginId of the selected smoothing plugin
    var smoothingPlugin: InputDropdownMenu = InputDropdownMenu(rh)

    // Skip the whole automation (no log entry) once the selected smoothing is
    // already active — the trigger stays true and would re-fire every cycle.
    override var precondition: Trigger? = TriggerSelectedSmoothingNotActive(injector) { smoothingPlugin.value }

    /** All installed smoothing plugins, same list as Configuration → Smoothing. */
    fun smoothingOptions(): List<PluginBase> = activePlugin.getSpecificPluginsList(PluginType.SMOOTHING)

    override fun friendlyName(): Int = R.string.change_smoothing
    override fun shortDescription(): String =
        if (smoothingPlugin.value.isEmpty()) rh.gs(R.string.change_smoothing)
        else rh.gs(R.string.change_smoothing_to, resolvePlugin()?.name ?: smoothingPlugin.value)

    override fun composeIcon() = Icons.Default.Timeline

    override suspend fun doAction(): PumpEnactResult {
        val plugin = resolvePlugin()
        if (plugin == null) {
            aapsLogger.error(LTag.AUTOMATION, "Smoothing plugin not found: ${smoothingPlugin.value}")
            return pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.error)
        }
        // The trigger can stay true and fire again. Do nothing when the plugin is
        // already active — a real switch would restart the full IobCob calculation.
        if (plugin.isEnabled(PluginType.SMOOTHING)) {
            aapsLogger.debug(LTag.AUTOMATION, "Smoothing already set to ${plugin.name}")
            return pumpEnactResultProvider.get().success(true).comment(R.string.alreadyset)
        }
        configBuilder.performPluginSwitch(plugin, true, PluginType.SMOOTHING)
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("smoothingPlugin", smoothingPlugin.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        smoothingPlugin.value = JsonHelper.safeGetString(o, "smoothingPlugin", "")
        return this
    }

    override fun isValid(): Boolean = resolvePlugin() != null

    private fun resolvePlugin(): PluginBase? =
        activePlugin.getSpecificPluginsList(PluginType.SMOOTHING).firstOrNull { it.pluginId == smoothingPlugin.value }
}
