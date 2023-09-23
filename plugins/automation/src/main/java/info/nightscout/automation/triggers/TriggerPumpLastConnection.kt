package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.core.utils.JsonHelper.safeGetInt
import info.nightscout.core.utils.JsonHelper.safeGetString
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

class TriggerPumpLastConnection(injector: HasAndroidInjector) : Trigger(injector) {

    var minutesAgo = InputDuration()
    var comparator = Comparator(rh)

    @Suppress("unused")
    constructor(injector: HasAndroidInjector, value: Int, unit: InputDuration.TimeUnit, compare: Comparator.Compare) : this(injector) {
        minutesAgo = InputDuration(value, unit)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerPumpLastConnection: TriggerPumpLastConnection) : this(injector) {
        minutesAgo = InputDuration(triggerPumpLastConnection.minutesAgo.value, triggerPumpLastConnection.minutesAgo.unit)
        comparator = Comparator(rh, triggerPumpLastConnection.comparator.value)
    }

    fun setValue(value: Int): TriggerPumpLastConnection {
        minutesAgo.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerPumpLastConnection {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val lastConnection = activePlugin.activePump.lastDataTime()
        if (lastConnection == 0L && comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        val connectionAgo = (dateUtil.now() - lastConnection) / (60 * 1000)
        aapsLogger.debug(LTag.AUTOMATION, "Last connection min ago: $connectionAgo")
        if (comparator.value.check(connectionAgo.toInt(), minutesAgo.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("minutesAgo", minutesAgo.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        minutesAgo.setMinutes(safeGetInt(d, "minutesAgo"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.automation_trigger_pump_last_connection_label

    override fun friendlyDescription(): String =
        rh.gs(R.string.automation_trigger_pump_last_connection_compared, rh.gs(comparator.value.stringRes), minutesAgo.value)

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.main.R.drawable.ic_remove)

    override fun duplicate(): Trigger = TriggerPumpLastConnection(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.automation_trigger_pump_last_connection_label, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.automation_trigger_pump_last_connection_description) + ": ", "", minutesAgo))
            .build(root)
    }
}