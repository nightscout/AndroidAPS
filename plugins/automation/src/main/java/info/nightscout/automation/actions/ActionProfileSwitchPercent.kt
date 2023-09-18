package info.nightscout.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputPercent
import info.nightscout.automation.elements.LabelWithElement
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.triggers.TriggerProfilePercent
import info.nightscout.core.utils.JsonHelper
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.LTag
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitchPercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uel: UserEntryLogger

    var pct = InputPercent()
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = R.string.profilepercentage
    override fun shortDescription(): String =
        if (duration.value == 0) rh.gs(R.string.startprofileforever, pct.value.toInt())
        else rh.gs(info.nightscout.core.ui.R.string.startprofile, pct.value.toInt(), duration.value)

    @DrawableRes override fun icon(): Int = info.nightscout.core.ui.R.drawable.ic_actions_profileswitch

    init {
        precondition = TriggerProfilePercent(injector, 100.0, Comparator.Compare.IS_EQUAL)
    }

    override fun doAction(callback: Callback) {
        if (profileFunction.createProfileSwitch(duration.value, pct.value.toInt(), 0)) {
            uel.log(
                UserEntry.Action.PROFILE_SWITCH,
                Sources.Automation,
                title + ": " + rh.gs(info.nightscout.core.ui.R.string.startprofile, pct.value.toInt(), duration.value),
                ValueWithUnit.Percent(pct.value.toInt()),
                ValueWithUnit.Minute(duration.value)
            )
            callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            callback.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.ok)).run()
        }
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.percent_u), "", pct))
            .add(LabelWithElement(rh, rh.gs(info.nightscout.core.ui.R.string.duration_min_label), "", duration))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject()
            .put("percentage", pct.value)
            .put("durationInMinutes", duration.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        pct.value = JsonHelper.safeGetDouble(o, "percentage")
        duration.value = JsonHelper.safeGetInt(o, "durationInMinutes")
        return this
    }

    override fun isValid(): Boolean =
        pct.value >= InputPercent.MIN &&
            pct.value <= InputPercent.MAX &&
            duration.value > 0
}