package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerSensorAge(deps: TriggerDeps) : Trigger(deps) {

    var sensorAgeHours: InputDouble = InputDouble(0.0, 0.0, 720.0, 0.1, NumberFormat.DECIMAL_1)
    var comparator: Comparator = Comparator(rh)

    private constructor(deps: TriggerDeps, triggerSensorAge: TriggerSensorAge) : this(deps) {
        sensorAgeHours = InputDouble(triggerSensorAge.sensorAgeHours)
        comparator = Comparator(rh, triggerSensorAge.comparator.value)
    }

    fun setValue(value: Double): TriggerSensorAge {
        sensorAgeHours.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerSensorAge {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val therapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
        val currentAgeHours = therapyEvent?.timestamp?.let { timestamp ->
            (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
        } ?: 0.0
        if (therapyEvent == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (therapyEvent == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(currentAgeHours, sensorAgeHours.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("sensorAgeHours", sensorAgeHours.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        sensorAgeHours.setValue(d.lenientDouble("sensorAgeHours"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.triggerSensorAgeLabel

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.triggerSensorAgeDesc, rh.gs(comparator.value.stringRes), sensorAgeHours.value)

    override fun composeIcon() = Icons.Filled.Sensors
    override fun elementType() = ElementType.SENSOR_INSERT

    override fun duplicate(): Trigger = TriggerSensorAge(deps, this)

}
