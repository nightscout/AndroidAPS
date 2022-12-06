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
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.utils.JsonHelper.safeGetString
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

class TriggerBolusAgo(injector: HasAndroidInjector) : Trigger(injector) {

    var minutesAgo: InputDuration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
    var comparator: Comparator = Comparator(rh)

    private constructor(injector: HasAndroidInjector, triggerBolusAgo: TriggerBolusAgo) : this(injector) {
        minutesAgo = InputDuration(triggerBolusAgo.minutesAgo.value, InputDuration.TimeUnit.MINUTES)
        comparator = Comparator(rh, triggerBolusAgo.comparator.value)
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
        val lastBolus = repository.getLastBolusRecordOfTypeWrapped(Bolus.Type.NORMAL).blockingGet()
        val lastBolusTime = if (lastBolus is ValueWrapper.Existing) lastBolus.value.timestamp else 0L
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
        rh.gs(R.string.lastboluscompared, rh.gs(comparator.value.stringRes), minutesAgo.getMinutes())

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.main.R.drawable.ic_bolus)

    override fun duplicate(): Trigger = TriggerBolusAgo(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.lastboluslabel, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.lastboluslabel) + ": ", rh.gs(info.nightscout.shared.R.string.unit_minutes), minutesAgo))
            .build(root)
    }
}