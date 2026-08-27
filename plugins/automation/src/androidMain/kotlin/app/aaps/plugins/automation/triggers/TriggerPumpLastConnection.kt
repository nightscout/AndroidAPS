package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDuration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerPumpLastConnection(deps: TriggerDeps) : Trigger(deps) {

    var minutesAgo = InputDuration()
    var comparator = Comparator(rh)

    @Suppress("unused")
    constructor(deps: TriggerDeps, value: Int, unit: InputDuration.TimeUnit, compare: Comparator.Compare) : this(deps) {
        minutesAgo = InputDuration(value, unit)
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, triggerPumpLastConnection: TriggerPumpLastConnection) : this(deps) {
        minutesAgo = InputDuration(triggerPumpLastConnection.minutesAgo.value, triggerPumpLastConnection.minutesAgo.unit)
        comparator = Comparator(rh, triggerPumpLastConnection.comparator.value)
    }

    fun setValue(value: Int): TriggerPumpLastConnection {
        minutesAgo.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerPumpLastConnection {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val lastConnection = activePlugin.activePump.lastDataTime.value
        if (lastConnection == 0L && comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        val connectionAgo = (dateUtil.now() - lastConnection) / (60 * 1000)
        aapsLogger.debug(LTag.AUTOMATION, "Last connection min ago: $connectionAgo")
        if (comparator.value.check(connectionAgo.toInt(), minutesAgo.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("minutesAgo", minutesAgo.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        minutesAgo.setMinutes(d.lenientInt("minutesAgo"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.automation_trigger_pump_last_connection_label

    override fun friendlyDescription(): String =
        rh.gs(R.string.automation_trigger_pump_last_connection_compared, rh.gs(comparator.value.stringRes), minutesAgo.value)

    override fun composeIcon() = Icons.Filled.SyncProblem
    override fun elementType() = ElementType.PUMP

    override fun duplicate(): Trigger = TriggerPumpLastConnection(deps, this)

}