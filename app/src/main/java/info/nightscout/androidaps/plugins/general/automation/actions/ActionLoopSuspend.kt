package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class ActionLoopSuspend(mainApp: MainApp) : Action(mainApp) {
    var minutes = InputDuration(0, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = R.string.suspendloop
    override fun shortDescription(): String = resourceHelper.gs(R.string.suspendloopforXmin, minutes.minutes)
    @DrawableRes override fun icon(): Int = R.drawable.ic_pause_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loopPlugin.isSuspended) {
            loopPlugin.suspendLoop(minutes.minutes)
            rxBus.send(EventRefreshOverview("ActionLoopSuspend"))
            callback.result(PumpEnactResult().success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult().success(true).comment(R.string.alreadysuspended))?.run()
        }
    }

    override fun toJSON(): String {
        val data = JSONObject().put("minutes", minutes.minutes)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        minutes.minutes = JsonHelper.safeGetInt(o, "minutes")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(resourceHelper.gs(R.string.careportal_newnstreatment_duration_min_label), "", minutes))
            .build(root)
    }
}