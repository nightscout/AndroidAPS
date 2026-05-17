package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputDateTime
import dagger.android.HasAndroidInjector
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

    override suspend fun shouldRun(): Boolean {
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

    override fun composeIcon() = Icons.Filled.Schedule
    override fun composeIconTint() = IconTint.Time

    override fun duplicate(): Trigger = TriggerTime(injector, time.value)

}