package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.InputDropdownMenu
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerStepsCount(injector: HasAndroidInjector) : Trigger(injector) {
    var measurementDuration: InputDropdownMenu = InputDropdownMenu(rh, "5")
    var stepsCount: InputDouble = InputDouble(100.0, 0.0, 20000.0, 10.0, DecimalFormat("1"))
    var comparator: Comparator = Comparator(rh).apply {
        value = Comparator.Compare.IS_EQUAL_OR_GREATER
    }

    override fun shouldRun(): Boolean {
        if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.info(LTag.AUTOMATION, "Steps count ready, no limit set ${friendlyDescription()}")
            return true
        }

        
        // Steps count entries update every 1-1.5 minutes on my watch,
        // so we must get some entries from the last 5 minutes.
        val start = dateUtil.now() - 5 * 60 * 1000L
        val measurements = persistenceLayer.getStepsCountFromTime(start)
        val lastSC = measurements.lastOrNull { it.duration == measurementDuration.value.toInt() * 60 * 1000L }
        if (lastSC == null) {
            aapsLogger.info(LTag.AUTOMATION, "No steps count measurements available - ${friendlyDescription()}")
            return false
        }

        val lastStepsCount: Int? = when (measurementDuration.value) {
            "5" -> lastSC.steps5min
            "10" -> lastSC.steps10min
            "15" -> lastSC.steps15min
            "30" -> lastSC.steps30min
            "60" -> lastSC.steps60min
            "180" -> lastSC.steps180min
            else -> null
        }

        if (lastStepsCount == null) {
            aapsLogger.info(LTag.AUTOMATION, "No steps count measurements available in selected period - ${friendlyDescription()}")
            return false
        }

        return comparator.value.check(lastStepsCount.toDouble(), stepsCount.value).also {
            aapsLogger.info(LTag.AUTOMATION, "Steps count ${if (it) "" else "not "}ready for $lastStepsCount in ${measurementDuration.value} minutes for ${friendlyDescription()}")
        }
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("stepsCount", stepsCount.value)
            .put("measurementDuration", measurementDuration.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        stepsCount.setValue(JsonHelper.safeGetDouble(d, "stepsCount"))
        measurementDuration.setValue(JsonHelper.safeGetString(d, "measurementDuration", "5"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerStepsCountLabel

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerStepsCountDesc, measurementDuration.value, rh.gs(comparator.value.stringRes), stepsCount.value)

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_cp_exercise)

    override fun duplicate(): Trigger {
        return TriggerStepsCount(injector).also { o ->
            o.stepsCount.setValue(stepsCount.value)
            o.measurementDuration.setValue(measurementDuration.value)
            o.comparator.setValue(comparator.value)
        }
    }

    override fun generateDialog(root: LinearLayout) {
        measurementDuration.setList(arrayListOf("5", "10", "15", "30", "60", "180"))
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.triggerStepsCountLabel, this))
            .add(LabelWithElement(rh, rh.gs(R.string.triggerStepsCountDropdownLabel) + ": ", rh.gs(app.aaps.core.interfaces.R.string.unit_minutes), measurementDuration))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.triggerStepsCountLabel) + ": ", "", stepsCount))
            .build(root)
    }
}
