package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerCannulaAge(injector: HasAndroidInjector) : Trigger(injector) {

    var cannulaAgeHours: InputDouble = InputDouble(0.0, 0.0, 336.0, 0.1, DecimalFormat("0.1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerCannulaAge: TriggerCannulaAge) : this(injector) {
        cannulaAgeHours = InputDouble(triggerCannulaAge.cannulaAgeHours)
        comparator = Comparator(rh, triggerCannulaAge.comparator.value)
    }

    fun setValue(value: Double): TriggerCannulaAge {
        cannulaAgeHours.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerCannulaAge {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val therapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)
        val currentAgeHours = therapyEvent?.timestamp?.let { timestamp ->
            (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
        } ?: 0.0
        if (therapyEvent == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (therapyEvent == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(currentAgeHours, cannulaAgeHours.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("cannulaAgeHours", cannulaAgeHours.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        cannulaAgeHours.setValue(safeGetDouble(d, "cannulaAgeHours"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerCannulaAgeLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerCannulaAgeDesc, rh.gs(comparator.value.stringRes), cannulaAgeHours.value)

    override fun icon(): Optional<Int> {
        val isPatchPump = activePlugin.activePump.pumpDescription.isPatchPump
        return if (isPatchPump) {
            Optional.of(app.aaps.core.objects.R.drawable.ic_patch_pump_outline)
        } else {
            Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_cannula)
        }
    }

    override fun duplicate(): Trigger = TriggerCannulaAge(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggerCannulaAgeLabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.triggerCannulaAgeLabel) + ": ", rh.gs(app.aaps.core.interfaces.R.string.unit_hour), cannulaAgeHours))
            .build(root)
    }
}
