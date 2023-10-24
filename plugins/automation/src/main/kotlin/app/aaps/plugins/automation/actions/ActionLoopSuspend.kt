package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import dagger.android.HasAndroidInjector
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
            loop.suspendLoop(
                durationInMinutes = minutes.getMinutes(),
                action = app.aaps.core.data.ue.Action.SUSPEND,
                source = Sources.Automation,
                note = title + ": " + rh.gs(R.string.suspendloopforXmin, minutes.getMinutes()),
                listValues = listOf(ValueWithUnit.Minute(minutes.getMinutes()))
            )
            rxBus.send(EventRefreshOverview("ActionLoopSuspend"))
            callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.alreadysuspended)).run()
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