package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import org.json.JSONObject

class TriggerBolusAgo(injector: HasAndroidInjector) : Trigger(injector) {

    var minutesAgo: InputDuration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
    var comparator: Comparator = Comparator(resourceHelper)

    private constructor(injector: HasAndroidInjector, triggerBolusAgo: TriggerBolusAgo) : this(injector) {
        minutesAgo = InputDuration(triggerBolusAgo.minutesAgo.value, InputDuration.TimeUnit.MINUTES)
        comparator = Comparator(resourceHelper, triggerBolusAgo.comparator.value)
    }

    fun setValue(value: Int): TriggerBolusAgo {
        minutesAgo.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerBolusAgo {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val lastBolusTime = repository.getLastBolusRecordOfType(Bolus.Type.NORMAL)?.timestamp ?: 0L
        if (lastBolusTime == 0L)
            return if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                true
            } else {
                aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
                false
            }
        val last = (dateUtil.now() - lastBolusTime).toDouble() / (60 * 1000)
        aapsLogger.debug(LTag.AUTOMATION, "LastBolus min ago: $minutesAgo")
        val doRun = comparator.value.check(last.toInt(), minutesAgo.getMinutes())
        if (doRun) {
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
        minutesAgo.setMinutes(JsonHelper.safeGetInt(d, "minutesAgo"))
        comparator.setValue(Comparator.Compare.valueOf(safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.lastboluslabel

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.lastboluscompared, resourceHelper.gs(comparator.value.stringRes), minutesAgo.getMinutes())

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_bolus)

    override fun duplicate(): Trigger = TriggerBolusAgo(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(resourceHelper, R.string.lastboluslabel, this))
            .add(comparator)
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.lastboluslabel) + ": ", "", minutesAgo))
            .build(root)
    }
}