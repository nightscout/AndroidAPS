package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta
import info.nightscout.automation.elements.InputDelta.DeltaType
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerDelta(injector: HasAndroidInjector) : Trigger(injector) {

    var units: GlucoseUnit = GlucoseUnit.MGDL
    var delta: InputDelta = InputDelta(rh)
    var comparator: Comparator = Comparator(rh)

    companion object {

        private const val MMOL_MAX = 4.0
        private const val MGDL_MAX = 72.0
    }

    init {
        units = profileFunction.getUnits()
        delta = if (units == GlucoseUnit.MMOL) InputDelta(rh, 0.0, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), DeltaType.DELTA)
        else InputDelta(rh, 0.0, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), DeltaType.DELTA)
    }

    constructor(injector: HasAndroidInjector, inputDelta: InputDelta, units: GlucoseUnit, comparator: Comparator.Compare) : this(injector) {
        this.units = units
        this.delta = inputDelta
        this.comparator.value = comparator
    }

    private constructor(injector: HasAndroidInjector, triggerDelta: TriggerDelta) : this(injector) {
        units = triggerDelta.units
        delta = InputDelta(rh, triggerDelta.delta)
        comparator = Comparator(rh, triggerDelta.comparator.value)
    }

    fun units(units: GlucoseUnit): TriggerDelta {
        this.units = units
        return this
    }

    fun setValue(value: Double, type: DeltaType): TriggerDelta {
        this.delta.value = value
        this.delta.deltaType = type
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerDelta {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            ?: return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        val calculatedDelta = when (delta.deltaType) {
            DeltaType.SHORT_AVERAGE -> glucoseStatus.shortAvgDelta
            DeltaType.LONG_AVERAGE  -> glucoseStatus.longAvgDelta
            else                    -> glucoseStatus.delta
        }
        if (comparator.value.check(calculatedDelta, profileUtil.convertToMgdl(delta.value, units))) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: delta is " + calculatedDelta + " " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("value", delta.value)
            .put("units", units.asText)
            .put("deltaType", delta.deltaType)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        units = GlucoseUnit.fromText(JsonHelper.safeGetString(d, "units", Constants.MGDL))
        val type = DeltaType.valueOf(JsonHelper.safeGetString(d, "deltaType", ""))
        val value = JsonHelper.safeGetDouble(d, "value")
        delta =
            if (units == GlucoseUnit.MMOL) InputDelta(rh, value, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), type)
            else InputDelta(rh, value, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), type)
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.deltalabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.deltacompared, rh.gs(comparator.value.stringRes), delta.value, rh.gs(delta.deltaType.stringRes))

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_auto_delta)

    override fun duplicate(): Trigger = TriggerDelta(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.deltalabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.deltalabel_u, units) + ": ", "", delta))
            .build(root)
    }
}