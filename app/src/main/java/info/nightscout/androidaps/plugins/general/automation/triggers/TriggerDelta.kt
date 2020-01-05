package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta.DeltaType
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerDelta(mainApp: MainApp) : Trigger(mainApp) {

    var units: String = Constants.MGDL
    private var delta: InputDelta = InputDelta(mainApp)
    var comparator: Comparator = Comparator(mainApp)

    companion object {
        private const val MMOL_MAX = 4.0
        private const val MGDL_MAX = 72.0
    }

    init {
        units = profileFunction.getUnits()
        delta = if (units == Constants.MMOL) InputDelta(mainApp, 0.0, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), DeltaType.DELTA)
        else InputDelta(mainApp, 0.0, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), DeltaType.DELTA)
    }

    private constructor(mainApp: MainApp, triggerDelta: TriggerDelta) : this(mainApp) {
        units = triggerDelta.units
        delta = InputDelta(mainApp, triggerDelta.delta)
        comparator = Comparator(mainApp, triggerDelta.comparator.value)
    }

    override fun shouldRun(): Boolean {
        val glucoseStatus = GlucoseStatus.getGlucoseStatusData()
            ?: return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        val calculatedDelta = when (delta.deltaType) {
            DeltaType.SHORT_AVERAGE -> glucoseStatus.short_avgdelta
            DeltaType.LONG_AVERAGE  -> glucoseStatus.long_avgdelta
            else                    -> glucoseStatus.delta
        }
        if (comparator.value.check(calculatedDelta, Profile.toMgdl(delta.value, units))) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: delta is " + calculatedDelta + " " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("value", delta.value)
            .put("units", units)
            .put("deltaType", delta.deltaType)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        units = JsonHelper.safeGetString(d, "units")!!
        val type = DeltaType.valueOf(JsonHelper.safeGetString(d, "deltaType", ""))
        val value = JsonHelper.safeGetDouble(d, "value")
        delta =
            if (units == Constants.MMOL) InputDelta(mainApp, value, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), type)
            else InputDelta(mainApp, value, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), type)
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.deltalabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.deltacompared, resourceHelper.gs(comparator.value.stringRes), delta.value, resourceHelper.gs(delta.deltaType.stringRes))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.icon_auto_delta)

    override fun duplicate(): Trigger = TriggerDelta(mainApp, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(mainApp, R.string.deltalabel, this))
            .add(comparator)
            .add(LabelWithElement(mainApp, resourceHelper.gs(R.string.deltalabel_u, units) + ": ", "", delta))
            .build(root)
    }
}