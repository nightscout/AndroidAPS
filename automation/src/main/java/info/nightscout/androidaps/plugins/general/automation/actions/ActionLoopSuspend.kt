package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionLoopSuspend(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    var minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = R.string.suspendloop
    override fun shortDescription(): String = rh.gs(R.string.suspendloopforXmin, minutes.getMinutes())
    @DrawableRes override fun icon(): Int = R.drawable.ic_pause_circle_outline_24dp

    override fun doAction(callback: Callback) {
        if (!loop.isSuspended) {
            loop.suspendLoop(minutes.getMinutes())
            rxBus.send(EventRefreshOverview("ActionLoopSuspend"))
            uel.log(
                UserEntry.Action.SUSPEND, Sources.Automation, title + ": " + rh.gs(R.string.suspendloopforXmin, minutes.getMinutes()),
                ValueWithUnit.Minute(minutes.getMinutes())
            )
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadysuspended))?.run()
        }
    }

    override fun toJSON(): String {
        val data = JSONObject().put("minutes", minutes.getMinutes())
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        minutes.setMinutes(JsonHelper.safeGetInt(o, "minutes"))
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.duration_min_label), "", minutes))
            .build(root)
    }

    override fun isValid(): Boolean = minutes.value > 5
}