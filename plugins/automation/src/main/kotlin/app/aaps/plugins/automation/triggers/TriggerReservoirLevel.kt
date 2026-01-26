package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
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

class TriggerReservoirLevel(injector: HasAndroidInjector) : Trigger(injector) {

    var reservoirLevel: InputDouble = InputDouble(0.0, 0.0, 800.0, 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerReservoirLevel: TriggerReservoirLevel) : this(injector) {
        reservoirLevel = InputDouble(triggerReservoirLevel.reservoirLevel)
        comparator = Comparator(rh, triggerReservoirLevel.comparator.value)
    }

    fun setValue(value: Double): TriggerReservoirLevel {
        reservoirLevel.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerReservoirLevel {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val iCfg = activePlugin.activeInsulin.iCfg
        val actualReservoirLevel = activePlugin.activePump.reservoirLevel.iU(iCfg.concentration)
        if (comparator.value.check(actualReservoirLevel, reservoirLevel.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false

    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("reservoirLevel", reservoirLevel.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        reservoirLevel.setValue(safeGetDouble(d, "reservoirLevel"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerReservoirLevelLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerReservoirLevelDesc, rh.gs(comparator.value.stringRes), reservoirLevel.value)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_insulin)

    override fun duplicate(): Trigger = TriggerReservoirLevel(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggerReservoirLevelLabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.triggerReservoirLevelLabel) + ": ", rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname), reservoirLevel))
            .build(root)
    }
}
