package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import org.json.JSONObject

class TriggerBolusAgo(mainApp: MainApp) : Trigger(mainApp) {
    private var minutesAgo: InputDuration = InputDuration(mainApp, 0, InputDuration.TimeUnit.MINUTES)
    var comparator: Comparator = Comparator(mainApp)

    private constructor(mainApp: MainApp, triggerBolusAgo: TriggerBolusAgo) : this(mainApp) {
        minutesAgo = InputDuration(mainApp, triggerBolusAgo.minutesAgo.value, InputDuration.TimeUnit.MINUTES)
        comparator = Comparator(mainApp, triggerBolusAgo.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val lastBolusTime = treatmentsPlugin.getLastBolusTime(false)
        if (lastBolusTime == 0L)
            return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        val last = (DateUtil.now() - lastBolusTime).toDouble() / (60 * 1000)
        aapsLogger.debug(LTag.AUTOMATION, "LastBolus min ago: $minutesAgo")
        val doRun = comparator.value.check(last.toInt(), minutesAgo.getMinutes())
        if (doRun) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("minutesAgo", minutesAgo.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        minutesAgo.setMinutes(JsonHelper.safeGetInt(d, "minutesAgo"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.lastboluslabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.lastboluscompared, resourceHelper.gs(comparator.value.stringRes), minutesAgo.getMinutes())

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.icon_bolus)

    override fun duplicate(): Trigger = TriggerBolusAgo(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.lastboluslabel, this))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.lastboluslabel) + ": ", "", minutesAgo))
            .build(root)
    }
}