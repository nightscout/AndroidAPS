package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerProfilePercent(mainApp: MainApp) : Trigger(mainApp) {
    private var pct = InputPercent(mainApp)
    var comparator = Comparator(mainApp)

    constructor(mainApp: MainApp, value: Double, compare: Comparator.Compare) : this(mainApp) {
        pct = InputPercent(mainApp, value)
        comparator = Comparator(mainApp, compare)
    }

    constructor(mainApp: MainApp, triggerProfilePercent: TriggerProfilePercent) : this(mainApp) {
        pct = InputPercent(mainApp, triggerProfilePercent.pct.value)
        comparator = Comparator(mainApp, triggerProfilePercent.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val profile = profileFunction.getProfile()
        if (profile == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (profile == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(profile.percentage.toDouble(), pct.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    @Synchronized override fun toJSON(): String {
        val data = JSONObject()
            .put("percentage", pct.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        pct.value = JsonHelper.safeGetDouble(d, "percentage")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.profilepercentage

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.percentagecompared, resourceHelper.gs(comparator.value.stringRes), pct.value.toInt())

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.icon_actions_profileswitch)

    override fun duplicate(): Trigger = TriggerProfilePercent(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.profilepercentage))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.percent_u), "", pct))
            .build(root)
    }
}