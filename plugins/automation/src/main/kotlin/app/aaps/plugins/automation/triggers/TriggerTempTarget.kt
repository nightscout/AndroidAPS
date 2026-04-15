package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.ComparatorExists
import dagger.android.HasAndroidInjector
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

    override suspend fun shouldRun(): Boolean {
        val tt = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        if (tt == null && comparator.value == ComparatorExists.Compare.NOT_EXISTS) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (tt != null && comparator.value == ComparatorExists.Compare.EXISTS) {
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

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.temporary_target

    override fun friendlyDescription(): String =
        rh.gs(R.string.temptargetcompared, rh.gs(comparator.value.stringRes))

    override fun composeIcon() = IcTtManual
    override fun composeIconTint() = IconTint.TempTarget

    override fun duplicate(): Trigger = TriggerTempTarget(injector, this)

}