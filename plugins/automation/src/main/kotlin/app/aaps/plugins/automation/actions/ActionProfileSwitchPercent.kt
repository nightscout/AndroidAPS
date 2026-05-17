package app.aaps.plugins.automation.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitchPercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var profileFunction: ProfileFunction

    var pct = InputPercent()
    var duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)

    override var precondition: Trigger? = TriggerProfilePercent(injector, 100.0, Comparator.Compare.IS_EQUAL)

    override fun friendlyName(): Int = R.string.profilepercentage
    override fun shortDescription(): String =
        if (duration.value == 0) rh.gs(R.string.startprofileforever, pct.value.toInt())
        else rh.gs(app.aaps.core.ui.R.string.startprofile, pct.value.toInt(), duration.value)

    override fun composeIcon() = IcProfile
    override fun composeIconTint() = IconTint.Profile

    override suspend fun doAction(): PumpEnactResult {
        val switched = profileFunction.createProfileSwitch(
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
        ) != null
        return if (switched) {
            pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.ok)
        }
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