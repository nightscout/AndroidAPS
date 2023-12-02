package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputBg
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional
import kotlin.math.roundToInt

class TriggerBg(injector: HasAndroidInjector) : Trigger(injector) {

    var bg = InputBg(profileFunction)
    var comparator = Comparator(rh)

    constructor(injector: HasAndroidInjector, value: Double, units: GlucoseUnit, compare: Comparator.Compare) : this(injector) {
        bg = InputBg(profileFunction, value, units)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerBg: TriggerBg) : this(injector) {
        bg = InputBg(profileFunction, triggerBg.bg.value, triggerBg.bg.units)
        comparator = Comparator(rh, triggerBg.comparator.value)
    }

    fun setUnits(units: GlucoseUnit): TriggerBg {
        bg.units = units
        return this
    }

    fun setValue(value: Double): TriggerBg {
        bg.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerBg {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (glucoseStatus == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(glucoseStatus.glucose.roundToInt(), profileUtil.convertToMgdl(bg.value, bg.units).roundToInt())) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("bg", bg.value)
            .put("comparator", comparator.value.toString())
            .put("units", bg.units.asText)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        bg.setUnits(GlucoseUnit.fromText(JsonHelper.safeGetString(d, "units", GlucoseUnit.MGDL.asText)))
        bg.value = JsonHelper.safeGetDouble(d, "bg")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.glucose

    override fun friendlyDescription(): String {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            rh.gs(R.string.glucoseisnotavailable)
        else
            rh.gs(if (bg.units == GlucoseUnit.MGDL) R.string.glucosecomparedmgdl else R.string.glucosecomparedmmol, rh.gs(comparator.value.stringRes), bg.value, bg.units)
    }

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.main.R.drawable.ic_cp_bgcheck)

    override fun duplicate(): Trigger = TriggerBg(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, app.aaps.core.ui.R.string.glucose, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.glucose_u, bg.units), "", bg))
            .build(root)
    }
}