package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.InputDateTime
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject

class TriggerTime(injector: HasAndroidInjector) : Trigger(injector) {

    var time = InputDateTime(resourceHelper, dateUtil)

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

    override fun friendlyName(): Int = R.string.time

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.atspecifiedtime, dateUtil.dateAndTimeString(time.value))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTime(injector, time.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(resourceHelper, R.string.time, this))
            .add(time)
            .build(root)
    }
}