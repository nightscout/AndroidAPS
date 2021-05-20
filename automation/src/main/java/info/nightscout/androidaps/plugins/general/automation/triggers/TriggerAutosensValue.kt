package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
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
    var autosens: InputDouble = InputDouble(100.0, minValue.toDouble(), maxValue.toDouble(), step, decimalFormat)

    var comparator: Comparator = Comparator(resourceHelper)

    private constructor(injector: HasAndroidInjector, triggerAutosensValue: TriggerAutosensValue) : this(injector) {
        autosens = InputDouble(triggerAutosensValue.autosens)
        comparator = Comparator(resourceHelper, triggerAutosensValue.comparator.value)
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
        resourceHelper.gs(R.string.autosenscompared, resourceHelper.gs(comparator.value.stringRes), autosens.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_as)

    override fun duplicate(): Trigger = TriggerAutosensValue(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(resourceHelper, R.string.autosenslabel, this))
            .add(comparator)
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.autosenslabel) + ": ", "", autosens))
            .build(root)
    }
}