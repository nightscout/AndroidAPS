package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerTempTarget(mainApp: MainApp) : Trigger(mainApp) {
    var comparator = ComparatorExists(mainApp)

    constructor(mainApp: MainApp, compare: ComparatorExists.Compare) : this(mainApp) {
        comparator = ComparatorExists(mainApp, compare)
    }

    constructor(mainApp: MainApp, triggerTempTarget: TriggerTempTarget) : this(mainApp) {
        comparator = ComparatorExists(mainApp, triggerTempTarget.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val tt = treatmentsPlugin.tempTargetFromHistory
        if (tt == null && comparator.value == ComparatorExists.Compare.NOT_EXISTS) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt != null && comparator.value == ComparatorExists.Compare.EXISTS) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", TriggerTempTarget::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        comparator.value = ComparatorExists.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.careportal_temporarytarget

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.temptargetcompared, resourceHelper.gs(comparator.value.stringRes))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_keyboard_tab)

    override fun duplicate(): Trigger = TriggerTempTarget(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.careportal_temporarytarget, this))
            .add(comparator)
            .build(root)
    }
}