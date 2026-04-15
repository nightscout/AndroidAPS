package app.aaps.plugins.automation.triggers

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.icons.IcPatchPump
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import dagger.android.HasAndroidInjector
import org.json.JSONObject

class TriggerPodChange(injector: HasAndroidInjector) : Trigger(injector) {

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

    override fun dataJSON(): JSONObject =
        JSONObject()

    override fun fromJSON(data: String): Trigger {
        return this
    }

    override fun friendlyName(): Int = R.string.triggerPodChangeLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerPodChangeDesc)

    override fun composeIcon() = IcPatchPump
    override fun composeIconTint() = IconTint.Device

    override fun duplicate(): Trigger = TriggerPodChange(injector)

}
