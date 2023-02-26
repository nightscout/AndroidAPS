package info.nightscout.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.automation.ui.TimerUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import javax.inject.Inject

class ActionAlarm(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var timerUtil: TimerUtil
    @Inject lateinit var config: Config

    var text = InputString()

    constructor(injector: HasAndroidInjector, text: String) : this(injector) {
        this.text = InputString(text)
    }

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.alarm
    override fun shortDescription(): String = rh.gs(R.string.alarm_message, text.value)
    @DrawableRes override fun icon(): Int = info.nightscout.core.main.R.drawable.ic_access_alarm_24dp

    override fun isValid(): Boolean = true // empty alarm will show app name

    override fun doAction(callback: Callback) {
        timerUtil.scheduleReminder(10, text.value.takeIf { it.isNotBlank() }
            ?: rh.gs(config.appName))
        callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        text.value = JsonHelper.safeGetString(o, "text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.alarm_short), "", text))
            .build(root)
    }
}