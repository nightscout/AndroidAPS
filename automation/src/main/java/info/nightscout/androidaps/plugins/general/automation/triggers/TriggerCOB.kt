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
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerCOB(injector: HasAndroidInjector) : Trigger(injector) {

    private val minValue = 0
    private val maxValue = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)
    var cob: InputDouble = InputDouble(0.0, minValue.toDouble(), maxValue.toDouble(), 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(resourceHelper)

    private constructor(injector: HasAndroidInjector, triggerCOB: TriggerCOB) : this(injector) {
        cob = InputDouble(triggerCOB.cob)
        comparator = Comparator(resourceHelper, triggerCOB.comparator.value)
    }

    fun setValue(value: Double): TriggerCOB {
        cob.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerCOB {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val cobInfo = iobCobCalculator.getCobInfo(false, "AutomationTriggerCOB")
        if (cobInfo.displayCob == null) {
            return if (comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        }
        if (cobInfo.displayCob != null && comparator.value.check(cobInfo.displayCob!!, cob.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("carbs", cob.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        cob.setValue(safeGetDouble(d, "carbs"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggercoblabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.cobcompared, resourceHelper.gs(comparator.value.stringRes), cob.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_cp_bolus_carbs)

    override fun duplicate(): Trigger = TriggerCOB(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(resourceHelper, R.string.triggercoblabel, this))
            .add(comparator)
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.triggercoblabel) + ": ", "", cob))
            .build(root)
    }
}