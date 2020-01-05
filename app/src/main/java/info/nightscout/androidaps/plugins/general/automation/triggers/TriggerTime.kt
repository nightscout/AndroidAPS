package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.InputDateTime
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject

class TriggerTime(mainApp: MainApp) : Trigger(mainApp) {
    var time = InputDateTime(mainApp)

    constructor(mainApp: MainApp, runAt: Long) : this(mainApp) {
        this.time.value = runAt
    }

    constructor(mainApp: MainApp, triggerTime: TriggerTime) : this(mainApp) {
        this.time.value = triggerTime.time.value
    }

    override fun shouldRun(): Boolean {
        val now = DateUtil.now()
        if (now >= time.value && now - time.value < T.mins(5).msecs()) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("runAt", time.value)
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        time.value = JsonHelper.safeGetLong(o, "runAt")
        return this
    }

    override fun friendlyName(): Int = R.string.time

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.atspecifiedtime, DateUtil.dateAndTimeString(time.value))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTime(mainApp, time.value)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.time))
            .add(time)
            .build(root)
    }
}