package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetInt
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import org.json.JSONObject

class TriggerPumpLastConnection(mainApp: MainApp) : Trigger(mainApp) {
    private var minutesAgo = InputDuration(mainApp)
    private var comparator = Comparator(mainApp)

    constructor(mainApp: MainApp, value: Int, unit: InputDuration.TimeUnit, compare: Comparator.Compare) : this(mainApp) {
        minutesAgo = InputDuration(mainApp, value, unit)
        comparator = Comparator(mainApp, compare)
    }

    constructor(mainApp: MainApp, triggerPumpLastConnection: TriggerPumpLastConnection) : this(mainApp) {
        minutesAgo = InputDuration(mainApp, triggerPumpLastConnection.minutesAgo.value, triggerPumpLastConnection.minutesAgo.unit)
        comparator = Comparator(mainApp, triggerPumpLastConnection.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val lastConnection = configBuilderPlugin.activePump?.lastDataTime() ?: return false
        if (lastConnection == 0L && comparator.value === Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        val connectionAgo = (DateUtil.now() - lastConnection) / (60 * 1000)
        aapsLogger.debug(LTag.AUTOMATION, "Last connection min ago: $connectionAgo")
        if (comparator.value.check(connectionAgo.toInt(), minutesAgo.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("minutesAgo", minutesAgo.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        minutesAgo.setMinutes(safeGetInt(d, "minutesAgo"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.automation_trigger_pump_last_connection_label

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.automation_trigger_pump_last_connection_compared, resourceHelper.gs(comparator.value.stringRes), minutesAgo.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.remove)

    override fun duplicate(): Trigger = TriggerPumpLastConnection(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.automation_trigger_pump_last_connection_label))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.automation_trigger_pump_last_connection_description) + ": ", "", minutesAgo))
            .build(root)
    }
}