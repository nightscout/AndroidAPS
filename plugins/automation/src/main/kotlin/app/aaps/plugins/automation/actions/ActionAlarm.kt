package app.aaps.plugins.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.ui.TimerUtil
import dagger.android.HasAndroidInjector
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

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.alarm
    override fun shortDescription(): String = rh.gs(R.string.alarm_message, text.value)
    @DrawableRes override fun icon(): Int = app.aaps.core.objects.R.drawable.ic_access_alarm_24dp

    override fun isValid(): Boolean = true // empty alarm will show app name

    override fun doAction(callback: Callback) {
        timerUtil.scheduleReminder(10, text.value.takeIf { it.isNotBlank() }
            ?: rh.gs(config.appName))
        callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
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