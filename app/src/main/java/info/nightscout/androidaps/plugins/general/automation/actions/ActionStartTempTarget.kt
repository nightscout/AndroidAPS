package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import info.nightscout.androidaps.utils.JsonHelper.safeGetInt
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import org.json.JSONObject

class ActionStartTempTarget(mainApp: MainApp) : Action(mainApp) {
    var value = InputTempTarget()
    var duration = InputDuration(0, InputDuration.TimeUnit.MINUTES)

    init {
        precondition = TriggerTempTarget().comparator(ComparatorExists.Compare.NOT_EXISTS)
    }

    override fun friendlyName(): Int = R.string.starttemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.starttemptarget) + ": " + tt().friendlyDescription(value.units)
    @DrawableRes override fun icon(): Int = R.drawable.icon_cp_cgm_target

    override fun doAction(callback: Callback) {
        treatmentsPlugin.addToHistoryTempTarget(tt())
        callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
    }

    override fun generateDialog(root: LinearLayout) {
        val unitResId = if (value.units == Constants.MGDL) R.string.mgdl else R.string.mmol
        LayoutBuilder()
            .add(LabelWithElement(resourceHelper.gs(R.string.careportal_temporarytarget) + "\n[" + resourceHelper.gs(unitResId) + "]", "", value))
            .add(LabelWithElement(resourceHelper.gs(R.string.careportal_newnstreatment_duration_min_label), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean {
        return true
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("value", value.value)
            .put("units", value.units)
            .put("durationInMinutes", duration.minutes)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        value.units = safeGetString(o, "units")
        value.value = safeGetDouble(o, "value")
        duration.minutes = safeGetInt(o, "durationInMinutes")
        return this
    }

    fun tt(): TempTarget =
        TempTarget()
            .date(DateUtil.now())
            .duration(duration.minutes)
            .reason("Automation")
            .source(Source.USER)
            .low(Profile.toMgdl(value.value, value.units))
            .high(Profile.toMgdl(value.value, value.units))
}