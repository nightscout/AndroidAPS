package info.nightscout.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import org.json.JSONObject
import javax.inject.Inject

class ActionLoopSuspend(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var loop: Loop
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger

    var minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.suspendloop
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
            callback.result(PumpEnactResult(injector).success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            callback.result(PumpEnactResult(injector).success(true).comment(R.string.alreadysuspended)).run()
        }
    }

    override fun toJSON(): String {
        val data = JSONObject().put("minutes", minutes.getMinutes())
        return JSONObject()
            .put("type", this.javaClass.simpleName)
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
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.duration_min_label), "", minutes))
            .build(root)
    }

    override fun isValid(): Boolean = minutes.value > 5
}