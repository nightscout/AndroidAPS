package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDouble
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerAutosensValue(injector: HasAndroidInjector) : Trigger(injector) {
    private val minValue = (sp.getDouble(R.string.key_openapsama_autosens_min, 0.7) * 100).toInt()
    private val maxValue = (sp.getDouble(R.string.key_openapsama_autosens_max, 1.2) * 100).toInt()
    private val step = 1.0
    private val decimalFormat = DecimalFormat("1")
    var autosens: InputDouble = InputDouble(injector, 100.0, minValue.toDouble(), maxValue.toDouble(), step, decimalFormat)

    var comparator: Comparator = Comparator(injector)

    private constructor(injector: HasAndroidInjector, triggerAutosensValue: TriggerAutosensValue) : this(injector) {
        autosens = InputDouble(injector, triggerAutosensValue.autosens)
        comparator = Comparator(injector, triggerAutosensValue.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val autosensData = iobCobCalculatorPlugin.getLastAutosensData("Automation trigger")
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

    override fun toJSON(): String {
        val data = JSONObject()
            .put("value", autosens.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        autosens.setValue(safeGetDouble(d, "value"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.autosenslabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.autosenscompared, resourceHelper.gs(comparator.value.stringRes), autosens.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.`ic_as`)

    override fun duplicate(): Trigger = TriggerAutosensValue(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.autosenslabel, this))
            .add(comparator)
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.autosenslabel) + ": ", "", autosens))
            .build(root)
    }
}