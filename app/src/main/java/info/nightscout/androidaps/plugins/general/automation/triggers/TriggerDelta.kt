package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
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

class TriggerDelta(injector: HasAndroidInjector) : Trigger(injector) {

    var units: String = Constants.MGDL
    var delta: InputDelta = InputDelta(injector)
    var comparator: Comparator = Comparator(injector)

    companion object {
        private const val MMOL_MAX = 4.0
        private const val MGDL_MAX = 72.0
    }

    init {
        units = profileFunction.getUnits()
        delta = if (units == Constants.MMOL) InputDelta(injector, 0.0, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), DeltaType.DELTA)
        else InputDelta(injector, 0.0, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), DeltaType.DELTA)
    }

    constructor(injector: HasAndroidInjector, inputDelta: InputDelta, units: String, comparator: Comparator.Compare) : this(injector) {
        this.units = units
        this.delta = inputDelta
        this.comparator.value = comparator
    }

    private constructor(injector: HasAndroidInjector, triggerDelta: TriggerDelta) : this(injector) {
        units = triggerDelta.units
        delta = InputDelta(injector, triggerDelta.delta)
        comparator = Comparator(injector, triggerDelta.comparator.value)
    }

    fun units(units: String): TriggerDelta {
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
        val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
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
            if (units == Constants.MMOL) InputDelta(injector, value, (-MMOL_MAX), MMOL_MAX, 0.1, DecimalFormat("0.1"), type)
            else InputDelta(injector, value, (-MGDL_MAX), MGDL_MAX, 1.0, DecimalFormat("1"), type)
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.deltalabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.deltacompared, resourceHelper.gs(comparator.value.stringRes), delta.value, resourceHelper.gs(delta.deltaType.stringRes))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_auto_delta)

    override fun duplicate(): Trigger = TriggerDelta(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.deltalabel, this))
            .add(comparator)
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.deltalabel_u, units) + ": ", "", delta))
            .build(root)
    }
}