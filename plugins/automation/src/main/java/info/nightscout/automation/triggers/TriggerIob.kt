package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputInsulin
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.core.utils.JsonHelper
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

class TriggerIob(injector: HasAndroidInjector) : Trigger(injector) {

    var insulin = InputInsulin()
    var comparator: Comparator = Comparator(rh)

    constructor(injector: HasAndroidInjector, triggerIob: TriggerIob) : this(injector) {
        insulin = InputInsulin(triggerIob.insulin)
        comparator = Comparator(rh, triggerIob.comparator.value)
    }

    fun setValue(value: Double): TriggerIob {
        insulin.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerIob {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val profile = profileFunction.getProfile() ?: return false
        val iob = iobCobCalculator.calculateFromTreatmentsAndTemps(dateUtil.now(), profile)
        if (comparator.value.check(iob.iob, insulin.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("insulin", insulin.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        insulin.value = JsonHelper.safeGetDouble(d, "insulin")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = info.nightscout.core.ui.R.string.iob

    override fun friendlyDescription(): String =
        rh.gs(R.string.iobcompared, rh.gs(comparator.value.stringRes), insulin.value)

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_keyboard_capslock)

    override fun duplicate(): Trigger = TriggerIob(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, info.nightscout.core.ui.R.string.iob, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.iob_u), "", insulin))
            .build(root)
    }
}