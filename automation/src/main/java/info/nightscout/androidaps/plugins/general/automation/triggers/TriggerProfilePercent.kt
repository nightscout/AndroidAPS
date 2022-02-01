package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputPercent
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject

class TriggerProfilePercent(injector: HasAndroidInjector) : Trigger(injector) {

    var pct = InputPercent()
    var comparator = Comparator(rh)

    constructor(injector: HasAndroidInjector, value: Double, compare: Comparator.Compare) : this(injector) {
        pct = InputPercent(value)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerProfilePercent: TriggerProfilePercent) : this(injector) {
        pct = InputPercent(triggerProfilePercent.pct.value)
        comparator = Comparator(rh, triggerProfilePercent.comparator.value)
    }

    fun setValue(value: Double): TriggerProfilePercent {
        this.pct.value = value
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerProfilePercent {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val profile = profileFunction.getProfile()
        if (profileFunction.isProfileChangePending()) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + "Profile change is already pending: " + friendlyDescription())
            return false
        }
        if (profile == null && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (profile == null) {
            aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
            return false
        }
        if (profile is ProfileSealed.EPS) {
            if (comparator.value.check(profile.value.originalPercentage.toDouble(), pct.value)) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                return true
            }
        }
        if (profile is ProfileSealed.Pure) {
            if (comparator.value.check(100.0, pct.value)) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                return true
            }
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("percentage", pct.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        pct.value = JsonHelper.safeGetDouble(d, "percentage")
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.profilepercentage

    override fun friendlyDescription(): String =
        rh.gs(R.string.percentagecompared, rh.gs(comparator.value.stringRes), pct.value.toInt())

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_actions_profileswitch)

    override fun duplicate(): Trigger = TriggerProfilePercent(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.profilepercentage, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.percent_u), "", pct))
            .build(root)
    }
}