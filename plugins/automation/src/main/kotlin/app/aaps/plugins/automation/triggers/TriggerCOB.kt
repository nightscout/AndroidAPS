package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerCOB(injector: HasAndroidInjector) : Trigger(injector) {

    private val minValue = 0
    private val maxValue = preferences.get(IntKey.SafetyMaxCarbs)
    var cob: InputDouble = InputDouble(0.0, minValue.toDouble(), maxValue.toDouble(), 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerCOB: TriggerCOB) : this(injector) {
        cob = InputDouble(triggerCOB.cob)
        comparator = Comparator(rh, triggerCOB.comparator.value)
    }

    fun setValue(value: Double): TriggerCOB {
        cob.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerCOB {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val cobInfo = iobCobCalculator.getCobInfo("AutomationTriggerCOB")
        if (cobInfo.displayCob == null) {
            return if (comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        }
        if (comparator.value.check(cobInfo.displayCob!!, cob.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("carbs", cob.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        cob.setValue(safeGetDouble(d, "carbs"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggercoblabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.cobcompared, rh.gs(comparator.value.stringRes), cob.value)

    override fun composeIcon() = IcCarbs
    override fun composeIconTint() = IconTint.Carbs

    override fun duplicate(): Trigger = TriggerCOB(injector, this)

}