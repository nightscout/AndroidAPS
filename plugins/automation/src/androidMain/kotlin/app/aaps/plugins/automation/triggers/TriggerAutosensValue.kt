package app.aaps.plugins.automation.triggers

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.DoubleKey
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerAutosensValue(deps: TriggerDeps) : Trigger(deps) {

    private val minValue = (preferences.get(DoubleKey.AutosensMin) * 100).toInt()
    private val maxValue = (preferences.get(DoubleKey.AutosensMax) * 100).toInt()
    private val step = 1.0
    private val decimalFormat = NumberFormat.INTEGER
    var autosens: InputDouble = InputDouble(100.0, minValue.toDouble(), maxValue.toDouble(), step, decimalFormat)

    var comparator: Comparator = Comparator(rh)

    private constructor(deps: TriggerDeps, triggerAutosensValue: TriggerAutosensValue) : this(deps) {
        autosens = InputDouble(triggerAutosensValue.autosens)
        comparator = Comparator(rh, triggerAutosensValue.comparator.value)
    }

    override suspend fun shouldRun(): Boolean {
        val autosensData = iobCobCalculator.ads.getLastAutosensData("Automation trigger", aapsLogger, dateUtil)
            ?: return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        if (comparator.value.check(autosensData.autosensResult.ratio, autosens.value / 100.0)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("value", autosens.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        autosens.setValue(d.lenientDouble("value"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.autosenslabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.autosenscompared, rh.gs(comparator.value.stringRes), autosens.value)

    override fun composeIcon() = IcAs
    override fun elementType() = ElementType.SENSITIVITY

    override fun duplicate(): Trigger = TriggerAutosensValue(deps, this)

}
