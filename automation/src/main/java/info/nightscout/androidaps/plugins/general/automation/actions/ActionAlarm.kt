package info.nightscout.androidaps.plugins.general.automation.actions

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.TimerUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import javax.inject.Inject

class ActionAlarm(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var context: Context
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var timerUtil: TimerUtil

    var text = InputString()

    constructor(injector: HasAndroidInjector, text: String) : this(injector) {
        this.text = InputString(text)
    }

    override fun friendlyName(): Int = R.string.alarm
    override fun shortDescription(): String = resourceHelper.gs(R.string.alarm_message, text.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_access_alarm_24dp

    override fun isValid(): Boolean = true // empty alarm will show app name

    override fun doAction(callback: Callback) {
        timerUtil.scheduleReminder(dateUtil.now() + T.secs(10L).msecs(), text.value.takeIf { it.isNotBlank() }
            ?: resourceHelper.gs(R.string.app_name))
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }

    override fun toJSON(): String {
        val data = JSONObject().put("text", text.value)
        return JSONObject()
            .put("type", this.javaClass.name)
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
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.alarm_short), "", text))
            .build(root)
    }
}