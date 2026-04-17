package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputProfileName
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class ActionProfileSwitch(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var insulin: Insulin
    @Inject lateinit var localProfileManager: LocalProfileManager
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil

    var inputProfileName: InputProfileName = InputProfileName(rh, localProfileManager, "")
    val iCfg: ICfg
        get() = insulin.iCfg         // use Current running iCfg, changing iCfg with Automation not allowed

    override fun friendlyName(): Int = R.string.profilename
    override fun shortDescription(): String = rh.gs(R.string.changengetoprofilename, inputProfileName.value)
    override fun composeIcon() = IcProfile
    override fun composeIconTint() = IconTint.Profile

    override suspend fun doAction(): PumpEnactResult {
        val activeProfileName = profileFunction.getProfileName()
        //Check for uninitialized profileName
        if (inputProfileName.value == "") {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile not initialized")
            return pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.error_field_must_not_be_empty)
        }
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            return pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.noprofile)
        }
        if (inputProfileName.value == activeProfileName) {
            aapsLogger.debug(LTag.AUTOMATION, "Profile is already switched")
            return pumpEnactResultProvider.get().success(true).comment(R.string.alreadyset)
        }
        val profileStore = localProfileManager.profile
            ?: return pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.noprofile)
        if (profileStore.getSpecificProfile(inputProfileName.value) == null) {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile does not exist! - ${inputProfileName.value}")
            return pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.notexists)
        }
        val result = profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = inputProfileName.value,
            durationInMinutes = 0,
            percentage = 100,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(), action = app.aaps.core.data.ue.Action.PROFILE_SWITCH,
            source = Sources.Automation,
            note = title,
            listValues = listOf(
                ValueWithUnit.SimpleString(inputProfileName.value),
                ValueWithUnit.Percent(100)
            ),
            iCfg = iCfg
        )
        return pumpEnactResultProvider.get().success(result != null).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject().put("profileToSwitchTo", inputProfileName.value)
        return JSONObject()
            .put("type", this.javaClass.simpleName)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        inputProfileName.value = JsonHelper.safeGetString(o, "profileToSwitchTo", "")
        return this
    }

    override fun isValid(): Boolean = localProfileManager.profile?.getSpecificProfile(inputProfileName.value) != null
}