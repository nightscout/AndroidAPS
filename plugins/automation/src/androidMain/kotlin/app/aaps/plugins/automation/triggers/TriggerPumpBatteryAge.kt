package app.aaps.plugins.automation.triggers

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerPumpBatteryAge(deps: TriggerDeps) : Trigger(deps) {

    var pumpBatteryAgeHours: InputDouble = InputDouble(0.0, 0.0, 336.0, 0.1, NumberFormat.DECIMAL_1)
    var comparator: Comparator = Comparator(rh)

    private constructor(deps: TriggerDeps, triggerPumpBatteryAge: TriggerPumpBatteryAge) : this(deps) {
        pumpBatteryAgeHours = InputDouble(triggerPumpBatteryAge.pumpBatteryAgeHours)
        comparator = Comparator(rh, triggerPumpBatteryAge.comparator.value)
    }

    fun setValue(value: Double): TriggerPumpBatteryAge {
        pumpBatteryAgeHours.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerPumpBatteryAge {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val therapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.PUMP_BATTERY_CHANGE)
        val currentAgeHours = therapyEvent?.timestamp?.let { timestamp ->
            (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
        } ?: 0.0
        val pump = activePlugin.activePump
        if (!pump.pumpDescription.isBatteryReplaceable && !pump.isBatteryChangeLoggingEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (therapyEvent == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (therapyEvent == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(currentAgeHours, pumpBatteryAgeHours.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("pumpBatteryAgeHours", pumpBatteryAgeHours.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        pumpBatteryAgeHours.setValue(d.lenientDouble("pumpBatteryAgeHours"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerPumpBatteryAgeLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerPumpBatteryAgeDesc, rh.gs(comparator.value.stringRes), pumpBatteryAgeHours.value)

    override fun composeIcon() = IcPumpBattery
    override fun elementType() = ElementType.BATTERY_CHANGE

    override fun duplicate(): Trigger = TriggerPumpBatteryAge(deps, this)

}
