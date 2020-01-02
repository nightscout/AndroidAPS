package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerProfilePercent
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class ActionProfileSwitchPercent(mainApp: MainApp) : Action(mainApp) {
    var pct = InputPercent()
    var duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = R.string.profilepercentage
    override fun shortDescription(): String =
        if (duration.minutes == 0) resourceHelper.gs(R.string.startprofileforever, pct.value.toInt())
        else resourceHelper.gs(R.string.startprofile, pct.value.toInt(), duration.minutes)

    @DrawableRes override fun icon(): Int = R.drawable.icon_actions_profileswitch

    init {
        precondition = TriggerProfilePercent().comparator(Comparator.Compare.IS_EQUAL).setValue(100.0)
    }

    override fun doAction(callback: Callback) {
        treatmentsPlugin.doProfileSwitch(duration.value.toInt(), pct.value.toInt(), 0)
        callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(resourceHelper.gs(R.string.percent_u), "", pct))
            .add(LabelWithElement(resourceHelper.gs(R.string.careportal_newnstreatment_duration_min_label), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject()
            .put("percentage", pct.value)
            .put("durationInMinutes", duration.minutes)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        pct.value = JsonHelper.safeGetDouble(o, "percentage")
        duration.minutes = JsonHelper.safeGetInt(o, "durationInMinutes")
        return this
    }
}