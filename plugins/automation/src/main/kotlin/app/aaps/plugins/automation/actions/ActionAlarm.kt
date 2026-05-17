package app.aaps.plugins.automation.actions

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.TimerUtil
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
    override fun composeIcon() = Icons.Filled.Alarm
    override fun composeIconTint() = IconTint.Alarm

    override fun isValid(): Boolean = true // empty alarm will show app name

    override suspend fun doAction(): PumpEnactResult {
        timerUtil.scheduleReminder(10, text.value.takeIf { it.isNotBlank() }
            ?: rh.gs(config.appName))
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
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

}