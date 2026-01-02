package app.aaps.plugins.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitchPercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var profileFunction: ProfileFunction

    var pct = InputPercent()
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override fun friendlyName(): Int = R.string.profilepercentage
    override fun shortDescription(): String =
        if (duration.value == 0) rh.gs(R.string.startprofileforever, pct.value.toInt())
        else rh.gs(app.aaps.core.ui.R.string.startprofile, pct.value.toInt(), duration.value)

    @DrawableRes override fun icon(): Int = app.aaps.core.ui.R.drawable.ic_actions_profileswitch_24dp

    init {
        precondition = TriggerProfilePercent(injector, 100.0, Comparator.Compare.IS_EQUAL)
    }

    override fun doAction(callback: Callback) {
        if (profileFunction.createProfileSwitch(
                durationInMinutes = duration.value,
                percentage = pct.value.toInt(),
                timeShiftInHours = 0,
                action = app.aaps.core.data.ue.Action.PROFILE_SWITCH,
                source = Sources.Automation,
                note = title + ": " + rh.gs(app.aaps.core.ui.R.string.startprofile, pct.value.toInt(), duration.value),
                listValues = listOf(
                    ValueWithUnit.Percent(pct.value.toInt()),
                    ValueWithUnit.Minute(duration.value)
                )
            )
        ) {
            callback.result(pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            callback.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.ok)).run()
        }
    }

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.percent_u), "", pct))
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.duration_min_label), "", duration))
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