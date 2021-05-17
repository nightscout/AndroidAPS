package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputInsulin
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerIob(injector: HasAndroidInjector) : Trigger(injector) {
    var insulin = InputInsulin()
    var comparator: Comparator = Comparator(resourceHelper)

    constructor(injector: HasAndroidInjector, triggerIob: TriggerIob) : this(injector) {
        insulin = InputInsulin(triggerIob.insulin)
        comparator = Comparator(resourceHelper, triggerIob.comparator.value)
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

    override fun friendlyName(): Int = R.string.iob

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.iobcompared, resourceHelper.gs(comparator.value.stringRes), insulin.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_keyboard_capslock)

    override fun duplicate(): Trigger = TriggerIob(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(resourceHelper, R.string.iob, this))
            .add(comparator)
            .add(LabelWithElement(resourceHelper, resourceHelper.gs(R.string.iob_u), "", insulin))
            .build(root)
    }
}