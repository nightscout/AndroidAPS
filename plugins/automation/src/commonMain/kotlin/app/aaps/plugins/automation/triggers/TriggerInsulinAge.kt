package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerInsulinAge(deps: TriggerDeps) : Trigger(deps) {

    var insulinAgeHours: InputDouble = InputDouble(0.0, 0.0, 336.0, 0.1, NumberFormat.DECIMAL_1)
    var comparator: Comparator = Comparator(rh)

    private constructor(deps: TriggerDeps, triggerInsulinAge: TriggerInsulinAge) : this(deps) {
        insulinAgeHours = InputDouble(triggerInsulinAge.insulinAgeHours)
        comparator = Comparator(rh, triggerInsulinAge.comparator.value)
    }

    fun setValue(value: Double): TriggerInsulinAge {
        insulinAgeHours.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerInsulinAge {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val therapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.INSULIN_CHANGE)
        val currentAgeHours = therapyEvent?.timestamp?.let { timestamp ->
            (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
        } ?: 0.0
        val isPatchPump = activePlugin.activePump.pumpDescription.isPatchPump
        if (isPatchPump) {
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
        if (comparator.value.check(currentAgeHours, insulinAgeHours.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("insulinAgeHours", insulinAgeHours.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        insulinAgeHours.setValue(d.lenientDouble("insulinAgeHours"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.triggerInsulinAgeLabel

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.triggerInsulinAgeDesc, rh.gs(comparator.value.stringRes), insulinAgeHours.value)

    override fun composeIcon() = Icons.Filled.HourglassBottom
    override fun elementType() = ElementType.FILL

    override fun duplicate(): Trigger = TriggerInsulinAge(deps, this)

}
