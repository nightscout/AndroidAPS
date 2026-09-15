package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcPatchPump
import app.aaps.plugins.automation.elements.Comparator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class TriggerPodChange(deps: TriggerDeps) : Trigger(deps) {

    override suspend fun shouldRun(): Boolean {
        val eventLastSettingsExport = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SETTINGS_EXPORT)
        val eventLastPodChange = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
        if (eventLastPodChange == null || eventLastSettingsExport == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution (no events): " + friendlyDescription())
            return false
        }
        // Check if settings export was done since last Pod change
        if (Comparator.Compare.IS_LESSER.check(eventLastSettingsExport.timestamp, eventLastPodChange.timestamp)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject { }

    override fun fromJSON(data: String): Trigger {
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.triggerPodChangeLabel

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.triggerPodChangeDesc)

    override fun composeIcon() = IcPatchPump
    override fun elementType() = ElementType.CANNULA_CHANGE

    override fun duplicate(): Trigger = TriggerPodChange(deps)

}
