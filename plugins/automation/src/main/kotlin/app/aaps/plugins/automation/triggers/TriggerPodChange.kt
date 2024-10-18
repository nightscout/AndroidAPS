package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional

class TriggerPodChange(injector: HasAndroidInjector) : Trigger(injector) {

    override fun shouldRun(): Boolean {
        val maxMinutesSinceCannulaChange = 6.0

        val therapyEventPodChange = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
        if (therapyEventPodChange == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution (1): " + friendlyDescription())
            return false
        }

        val currentPodChangeMinutes = therapyEventPodChange.timestamp?.let { timestamp ->
            (dateUtil.now() - timestamp) / (60 * 1000.0)
        }?.toDouble() ?: 0.0

        if (Comparator.Compare.IS_LESSER.check(currentPodChangeMinutes, maxMinutesSinceCannulaChange)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }

        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution (2): " + friendlyDescription())
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

    override fun icon(): Optional<Int> {
        val isPatchPump = activePlugin.activePump.pumpDescription.isPatchPump
        return if (isPatchPump) {
            Optional.of(app.aaps.core.objects.R.drawable.ic_patch_pump_outline)
        } else {
            Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_cannula)
        }
    }

    override fun duplicate(): Trigger = TriggerPodChange(injector)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggerPodChangeLabel, this))
            .add(LabelWithElement(rh, rh.gs(R.string.triggerPodChangeLabel)))
            .build(root)
    }
}
