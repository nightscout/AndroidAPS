package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerInsulinAge(injector: HasAndroidInjector) : Trigger(injector) {

    var insulinAgeHours: InputDouble = InputDouble(0.0, 0.0, 336.0, 0.1, DecimalFormat("0.1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerInsulinAge: TriggerInsulinAge) : this(injector) {
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

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("insulinAgeHours", insulinAgeHours.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        insulinAgeHours.setValue(safeGetDouble(d, "insulinAgeHours"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerInsulinAgeLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerInsulinAgeDesc, rh.gs(comparator.value.stringRes), insulinAgeHours.value)

    override fun composeIcon() = Icons.Filled.HourglassBottom
    override fun composeIconTint() = IconTint.Device

    override fun duplicate(): Trigger = TriggerInsulinAge(injector, this)

}
