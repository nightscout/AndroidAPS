package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputInsulin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerIob(deps: TriggerDeps) : Trigger(deps) {

    var insulin = InputInsulin()
    var comparator: Comparator = Comparator(rh)

    constructor(deps: TriggerDeps, triggerIob: TriggerIob) : this(deps) {
        insulin = InputInsulin(triggerIob.insulin)
        comparator = Comparator(rh, triggerIob.comparator.value)
    }

    fun setValue(value: Double): TriggerIob {
        insulin.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerIob {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val profile = profileFunction.getProfile() ?: return false
        val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(dateUtil.now(), profile)
        if (comparator.value.check(iob.iob, insulin.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("insulin", insulin.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        insulin.value = d.lenientDouble("insulin")
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.iob

    override fun friendlyDescription(): String =
        rh.gs(R.string.iobcompared, rh.gs(comparator.value.stringRes), insulin.value)

    override fun composeIcon() = IcBolus
    override fun elementType() = ElementType.INSULIN

    override fun duplicate(): Trigger = TriggerIob(deps, this)

}