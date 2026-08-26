package app.aaps.plugins.automation.actions

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import app.aaps.core.interfaces.alerts.ReminderScheduler
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Provider

class ActionAlarm(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val rxBus: RxBus,
    private val context: Context,
    private val dateUtil: DateUtil,
    private val reminderScheduler: ReminderScheduler,
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
        reminderScheduler: ReminderScheduler,
        config: Config,
        text: String
    ) : this(aapsLogger, rh, pumpEnactResultProvider, rxBus, context, dateUtil, reminderScheduler, config) {
        this.text = InputString(text)
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.alarm
    override fun shortDescription(): String = rh.gs(R.string.alarm_message, text.value)
    override fun composeIcon() = Icons.Filled.Alarm
    override fun elementType() = ElementType.BG_CHECK

    override fun isValid(): Boolean = true // empty alarm will show app name

    override suspend fun doAction(): PumpEnactResult {
        reminderScheduler.scheduleReminder(10, text.value.takeIf { it.isNotBlank() }
            ?: rh.gs(config.appName))
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionAlarm.javaClass.simpleName)
            put("data", buildJsonObject { put("text", text.value) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        text.value = o.lenientString("text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

}