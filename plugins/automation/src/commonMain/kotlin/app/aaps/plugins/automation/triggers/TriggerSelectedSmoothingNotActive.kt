package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import kotlinx.serialization.json.JsonObject

/**
 * True when the smoothing plugin selected in the action is installed but not active.
 * Used only as a precondition by [app.aaps.plugins.automation.actions.ActionSmoothingChange],
 * so the whole automation is skipped (no log entry, no re-run) once the selected
 * smoothing is already active. Not user-selectable and never serialized —
 * preconditions are rebuilt from the action class.
 */
class TriggerSelectedSmoothingNotActive(deps: TriggerDeps, private val selectedPluginId: () -> String) : Trigger(deps) {

    override suspend fun shouldRun(): Boolean {
        val plugin = activePlugin.getSpecificPluginsList(PluginType.SMOOTHING).firstOrNull { it.pluginId == selectedPluginId() }
        val ready = plugin != null && !plugin.isEnabled(PluginType.SMOOTHING)
        aapsLogger.debug(
            LTag.AUTOMATION,
            (if (ready) "Ready for execution: " else "NOT ready for execution: ") + friendlyDescription()
        )
        return ready
    }

    override fun dataJSON(): JsonObject = JsonObject(emptyMap())
    override fun fromJSON(data: String): Trigger = this

    override fun friendlyName(): TextRef = AutomationStrings.precondition_smoothing_not_active_name
    override fun friendlyDescription(): String {
        val name = activePlugin.getSpecificPluginsList(PluginType.SMOOTHING)
            .firstOrNull { it.pluginId == selectedPluginId() }?.name ?: selectedPluginId()
        return rh.gs(AutomationStrings.precondition_smoothing_not_active, name)
    }

    override fun composeIcon() = Icons.Default.Timeline

    override fun duplicate(): Trigger = TriggerSelectedSmoothingNotActive(deps, selectedPluginId)
}
