package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.data.model.GlucoseUnit
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

class TriggerTempTargetValue(injector: HasAndroidInjector) : Trigger(injector) {

    var ttValue = InputBg(profileFunction)
    var comparator = Comparator(rh)

    constructor(injector: HasAndroidInjector, value: Double, units: GlucoseUnit, compare: Comparator.Compare) : this(injector) {
        ttValue = InputBg(profileFunction, value, units)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerTempTarget: TriggerTempTargetValue) : this(injector) {
        ttValue = InputBg(profileFunction, triggerTempTarget.ttValue.value, triggerTempTarget.ttValue.units)
        comparator = Comparator(rh, triggerTempTarget.comparator.value)
    }

    fun comparator(comparator: Comparator.Compare): TriggerTempTargetValue {
        this.comparator.value = comparator
        return this
    }

    fun setUnits(units: GlucoseUnit): TriggerTempTargetValue {
        ttValue.units = units
        return this
    }

    fun setValue(value: Double): TriggerTempTargetValue {
        ttValue.value = value
        return this
    }

    override fun shouldRun(): Boolean {
        val tt = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        if (tt == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt != null && comparator.value.check(tt.lowTarget.roundToInt(), profileUtil.convertToMgdl(ttValue.value, ttValue.units).roundToInt())) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("tt", ttValue.value)
            .put("comparator", comparator.value.toString())
            .put("units", ttValue.units.asText)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        ttValue.setUnits(GlucoseUnit.fromText(JsonHelper.safeGetString(d, "units", GlucoseUnit.MGDL.asText)))
        ttValue.value = JsonHelper.safeGetDouble(d, "tt")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.careportal_temporarytargetvalue

    override fun friendlyDescription(): String {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            rh.gs(R.string.notemptarget)
        else
            rh.gs(if (ttValue.units == GlucoseUnit.MGDL) R.string.temptargetcomparedmgdl else R.string.temptargetcomparedmmol, rh.gs(comparator.value.stringRes), ttValue.value, ttValue.units)
    }

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_keyboard_tab)

    override fun duplicate(): Trigger = TriggerTempTargetValue(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, app.aaps.core.ui.R.string.careportal_temporarytargetvalue, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.target_u, ttValue.units), "", ttValue))
            .build(root)
    }
}