package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.T
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDateTime
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional

class TriggerTime(injector: HasAndroidInjector) : Trigger(injector) {

    var time = InputDateTime(rh, dateUtil)

    constructor(injector: HasAndroidInjector, runAt: Long) : this(injector) {
        this.time.value = runAt
    }

    @Suppress("unused")
    constructor(injector: HasAndroidInjector, triggerTime: TriggerTime) : this(injector) {
        this.time.value = triggerTime.time.value
    }

    fun runAt(time: Long): TriggerTime {
        this.time.value = time
        return this
    }

    override fun shouldRun(): Boolean {
        val now = dateUtil.now()
        if (now >= time.value && now - time.value < T.mins(5).msecs()) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("runAt", time.value)

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        time.value = JsonHelper.safeGetLong(o, "runAt")
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.time

    override fun friendlyDescription(): String =
        rh.gs(R.string.atspecifiedtime, dateUtil.dateAndTimeString(time.value))

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.main.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTime(injector, time.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, app.aaps.core.ui.R.string.time, this))
            .add(time)
            .build(root)
    }
}