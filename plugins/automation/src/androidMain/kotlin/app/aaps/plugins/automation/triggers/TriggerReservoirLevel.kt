package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerReservoirLevel(deps: TriggerDeps) : Trigger(deps) {

    var reservoirLevel: InputDouble = InputDouble(0.0, 0.0, 800.0, 1.0, NumberFormat.INTEGER)
    var comparator: Comparator = Comparator(rh)

    private constructor(deps: TriggerDeps, triggerReservoirLevel: TriggerReservoirLevel) : this(deps) {
        reservoirLevel = InputDouble(triggerReservoirLevel.reservoirLevel)
        comparator = Comparator(rh, triggerReservoirLevel.comparator.value)
    }

    fun setValue(value: Double): TriggerReservoirLevel {
        reservoirLevel.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerReservoirLevel {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        // The reservoir reading is converted with the insulin's concentration, so the wrong insulin misreads it by
        // the concentration ratio (U200 vs U100 = 2x) and fires — or fails to fire — at the wrong level. With nothing
        // in force there is no correct conversion, so don't run rather than guess.
        val iCfg = profileFunction.getRunningOrRequestedICfg() ?: run {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution, no insulin in use: " + friendlyDescription())
            return false
        }
        val actualReservoirLevel = activePlugin.activePump.reservoirLevel.value.iU(iCfg.concentration)
        if (comparator.value.check(actualReservoirLevel, reservoirLevel.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false

    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("reservoirLevel", reservoirLevel.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        reservoirLevel.setValue(d.lenientDouble("reservoirLevel"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.triggerReservoirLevelLabel

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.triggerReservoirLevelDesc, rh.gs(comparator.value.stringRes), reservoirLevel.value)

    override fun composeIcon() = IcPumpCartridge
    override fun elementType() = ElementType.FILL

    override fun duplicate(): Trigger = TriggerReservoirLevel(deps, this)

}
