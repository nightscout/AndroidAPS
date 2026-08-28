package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.icons.IcTtManual
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

class TriggerTempTargetValue(deps: TriggerDeps) : Trigger(deps) {

    var ttValue = InputBg(profileFunction)
    var comparator = Comparator(rh)

    constructor(deps: TriggerDeps, value: Double, units: GlucoseUnit, compare: Comparator.Compare) : this(deps) {
        ttValue = InputBg(profileFunction, value, units)
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, triggerTempTarget: TriggerTempTargetValue) : this(deps) {
        ttValue = InputBg(profileFunction, triggerTempTarget.ttValue.value, triggerTempTarget.ttValue.units)
        comparator = Comparator(rh, triggerTempTarget.comparator.value)
    }

    fun comparator(comparator: Comparator.Compare): TriggerTempTargetValue {
        this.comparator.value = comparator
        return this
    }

    fun setUnits(units: GlucoseUnit): TriggerTempTargetValue {
        ttValue.units = units
        return this
    }

    fun setValue(value: Double): TriggerTempTargetValue {
        ttValue.value = value
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val tt = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        if (tt == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt != null && comparator.value.check(tt.lowTarget.roundToInt(), profileUtil.convertToMgdl(ttValue.value, ttValue.units).roundToInt())) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("tt", ttValue.value)
            put("comparator", comparator.value.toString())
            put("units", ttValue.units.asText)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        ttValue.setUnits(GlucoseUnit.fromText(d.lenientString("units", GlucoseUnit.MGDL.asText)))
        ttValue.value = d.lenientDouble("tt")
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = CoreUiStrings.careportal_temporarytargetvalue

    override fun friendlyDescription(): String {
        return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE)
            rh.gs(AutomationStrings.notemptarget)
        else
            rh.gs(if (ttValue.units == GlucoseUnit.MGDL) R.string.temptargetcomparedmgdl else R.string.temptargetcomparedmmol, rh.gs(comparator.value.stringRes), ttValue.value, ttValue.units)
    }

    override fun composeIcon() = IcTtManual
    override fun elementType() = ElementType.TEMP_TARGET_MANAGEMENT

    override fun duplicate(): Trigger = TriggerTempTargetValue(deps, this)

}