package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputPercent
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional
import kotlin.math.roundToInt

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
            if (comparator.value.check(profile.value.originalPercentage, pct.value.roundToInt())) {
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

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.ui.R.drawable.ic_actions_profileswitch)

    override fun duplicate(): Trigger = TriggerProfilePercent(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.profilepercentage, this))
            .add(comparator)
            .add(LabelWithElement(rh, rh.gs(R.string.percent_u), "", pct))
            .build(root)
    }
}