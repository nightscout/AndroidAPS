package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerTempTarget(injector: HasAndroidInjector) : Trigger(injector) {

    var comparator = ComparatorExists(rh)

    constructor(injector: HasAndroidInjector, compare: ComparatorExists.Compare) : this(injector) {
        comparator = ComparatorExists(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerTempTarget: TriggerTempTarget) : this(injector) {
        comparator = ComparatorExists(rh, triggerTempTarget.comparator.value)
    }

    fun comparator(comparator: ComparatorExists.Compare): TriggerTempTarget {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val tt = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        if (tt is ValueWrapper.Absent && comparator.value == ComparatorExists.Compare.NOT_EXISTS) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt is ValueWrapper.Existing && comparator.value == ComparatorExists.Compare.EXISTS) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        comparator.value = ComparatorExists.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.careportal_temporarytarget

    override fun friendlyDescription(): String =
        rh.gs(R.string.temptargetcompared, rh.gs(comparator.value.stringRes))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_keyboard_tab)

    override fun duplicate(): Trigger = TriggerTempTarget(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.careportal_temporarytarget, this))
            .add(comparator)
            .build(root)
    }
}