package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.JsonHelper.safeGetDouble
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerPumpBatteryLevel(injector: HasAndroidInjector) : Trigger(injector) {

    var pumpBatteryLevel: InputDouble = InputDouble(0.0, 0.0, 100.0, 1.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerPumpBatteryLevel: TriggerPumpBatteryLevel) : this(injector) {
        pumpBatteryLevel = InputDouble(triggerPumpBatteryLevel.pumpBatteryLevel)
        comparator = Comparator(rh, triggerPumpBatteryLevel.comparator.value)
    }

    fun setValue(value: Double): TriggerPumpBatteryLevel {
        pumpBatteryLevel.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerPumpBatteryLevel {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val pump = activePlugin.activePump
        val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump.isUseRileyLinkBatteryLevel()
        val currentLevel = pump.batteryLevel?.toDouble()
        val available = currentLevel != null && (pump.model().supportBatteryLevel || erosBatteryLinkAvailable)
        if (!available) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (comparator.value.check(currentLevel, pumpBatteryLevel.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("pumpBatteryLevel", pumpBatteryLevel.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        pumpBatteryLevel.setValue(safeGetDouble(d, "pumpBatteryLevel"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerPumpBatteryLevelLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerPumpBatteryLevelDesc, rh.gs(comparator.value.stringRes), pumpBatteryLevel.value)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_cp_age_battery)

    override fun duplicate(): Trigger = TriggerPumpBatteryLevel(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggerPumpBatteryLevelLabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.triggerPumpBatteryLevelLabel) + ": ", "%", pumpBatteryLevel))
            .build(root)
    }
}
