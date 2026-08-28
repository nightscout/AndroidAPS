package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientString
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputBg
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

class TriggerBg(deps: TriggerDeps) : Trigger(deps) {

    var bg = InputBg(profileFunction)
    var comparator = Comparator(rh)

    constructor(deps: TriggerDeps, value: Double, units: GlucoseUnit, compare: Comparator.Compare) : this(deps) {
        bg = InputBg(profileFunction, value, units)
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, triggerBg: TriggerBg) : this(deps) {
        bg = InputBg(profileFunction, triggerBg.bg.value, triggerBg.bg.units)
        comparator = Comparator(rh, triggerBg.comparator.value)
    }

    fun setUnits(units: GlucoseUnit): TriggerBg {
        bg.units = units
        return this
    }

    fun setValue(value: Double): TriggerBg {
        bg.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerBg {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (glucoseStatus == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(glucoseStatus.glucose.roundToInt(), profileUtil.convertToMgdl(bg.value, bg.units).roundToInt())) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("bg", bg.value)
            put("comparator", comparator.value.toString())
            put("units", bg.units.asText)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        bg.setUnits(GlucoseUnit.fromText(d.lenientString("units", GlucoseUnit.MGDL.asText)))
        bg.value = d.lenientDouble("bg")
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = CoreUiStrings.glucose

    override fun friendlyDescription(): String {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            rh.gs(AutomationStrings.glucoseisnotavailable)
        else
            rh.gs(if (bg.units == GlucoseUnit.MGDL) AutomationStrings.glucosecomparedmgdl else AutomationStrings.glucosecomparedmmol, rh.gs(comparator.value.stringRes), bg.value, bg.units)
    }

    override fun composeIcon() = IcBgCheck
    override fun elementType() = ElementType.BG_CHECK

    override fun duplicate(): Trigger = TriggerBg(deps, this)

}