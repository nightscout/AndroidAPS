package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDateTime
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.T
import org.json.JSONObject

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

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.time

    override fun friendlyDescription(): String =
        rh.gs(R.string.atspecifiedtime, dateUtil.dateAndTimeString(time.value))

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTime(injector, time.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, info.nightscout.core.ui.R.string.time, this))
            .add(time)
            .build(root)
    }
}