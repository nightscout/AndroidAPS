package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.core.utils.JsonHelper.safeGetString
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

class TriggerAutosensValue(injector: HasAndroidInjector) : Trigger(injector) {

    private val minValue = (sp.getDouble(app.aaps.core.utils.R.string.key_openapsama_autosens_min, 0.7) * 100).toInt()
    private val maxValue = (sp.getDouble(app.aaps.core.utils.R.string.key_openapsama_autosens_max, 1.2) * 100).toInt()
    private val step = 1.0
    private val decimalFormat = DecimalFormat("1")
    var autosens: InputDouble = InputDouble(100.0, minValue.toDouble(), maxValue.toDouble(), step, decimalFormat)

    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerAutosensValue: TriggerAutosensValue) : this(injector) {
        autosens = InputDouble(triggerAutosensValue.autosens)
        comparator = Comparator(rh, triggerAutosensValue.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val autosensData = iobCobCalculator.ads.getLastAutosensData("Automation trigger", aapsLogger, dateUtil)
            ?: return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        if (comparator.value.check(autosensData.autosensResult.ratio, autosens.value / 100.0)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("value", autosens.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        autosens.setValue(safeGetDouble(d, "value"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.autosenslabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.autosenscompared, rh.gs(comparator.value.stringRes), autosens.value)

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_as)

    override fun duplicate(): Trigger = TriggerAutosensValue(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.autosenslabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.autosenslabel) + ": ", "", autosens))
            .build(root)
    }
}