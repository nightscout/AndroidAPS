package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
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

class TriggerCOB(mainApp: MainApp) : Trigger(mainApp) {
    private val minValue = 0
    private val maxValue = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)
    private var cob: InputDouble = InputDouble(mainApp, 0.0, minValue.toDouble(), maxValue.toDouble(), 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(mainApp)

    private constructor(mainApp: MainApp, triggerCOB: TriggerCOB) : this(mainApp) {
        cob = InputDouble(mainApp, triggerCOB.cob)
        comparator = Comparator(mainApp, triggerCOB.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "AutomationTriggerCOB")
        if (cobInfo.displayCob == null) {
            return if (comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        }
        if (comparator.value.check(cobInfo.displayCob, cob.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    @Synchronized override fun toJSON(): String {
        val data = JSONObject()
            .put("carbs", cob.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        cob.setValue(safeGetDouble(d, "carbs"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggercoblabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.cobcompared, resourceHelper.gs(comparator.value.stringRes), cob.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.icon_cp_bolus_carbs)

    override fun duplicate(): Trigger = TriggerCOB(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.triggercoblabel, this))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.triggercoblabel) + ": ", "", cob))
            .build(root)
    }
}