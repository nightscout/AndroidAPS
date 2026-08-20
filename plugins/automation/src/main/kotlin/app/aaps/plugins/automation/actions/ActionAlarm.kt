package app.aaps.plugins.automation.actions

import javax.inject.Provider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.TimerUtil
import org.json.JSONObject

class ActionAlarm(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val rxBus: RxBus,
    private val context: Context,
    private val dateUtil: DateUtil,
    private val timerUtil: TimerUtil,
    private val config: Config
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var text = InputString()

    constructor(
        aapsLogger: AAPSLogger,
        rh: ResourceHelper,
        pumpEnactResultProvider: Provider<PumpEnactResult>,
        rxBus: RxBus,
        context: Context,
        dateUtil: DateUtil,
        timerUtil: TimerUtil,
        config: Config,
        text: String
    ) : this(aapsLogger, rh, pumpEnactResultProvider, rxBus, context, dateUtil, timerUtil, config) {
        this.text = InputString(text)
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.alarm
    override fun shortDescription(): String = rh.gs(R.string.alarm_message, text.value)
    override fun composeIcon() = Icons.Filled.Alarm
    override fun elementType() = ElementType.BG_CHECK

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