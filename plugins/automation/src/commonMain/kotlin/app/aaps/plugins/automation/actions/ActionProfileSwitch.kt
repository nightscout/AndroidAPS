package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.elements.InputProfileName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionProfileSwitch(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val profileRepository: ProfileRepository,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var inputProfileName: InputProfileName = InputProfileName("")

    override fun friendlyName(): TextRef = AutomationStrings.profilename
    override fun shortDescription(): String = rh.gs(AutomationStrings.changengetoprofilename, inputProfileName.value)
    override fun composeIcon() = IcProfile
    override fun elementType() = ElementType.PROFILE_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult {
        val activeProfileName = profileFunction.getProfileName()
        //Check for uninitialized profileName
        if (inputProfileName.value == "") {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile not initialized")
            return pumpEnactResultProvider().success(false).comment(CoreUiStrings.error_field_must_not_be_empty)
        }
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            return pumpEnactResultProvider().success(false).comment(CoreUiStrings.noprofile)
        }
        if (inputProfileName.value == activeProfileName) {
            aapsLogger.debug(LTag.AUTOMATION, "Profile is already switched")
            return pumpEnactResultProvider().success(true).comment(AutomationStrings.alreadyset)
        }
        // Keep whatever insulin is in force — automation must not change it. The guard above already refuses when
        // no profile is running, so this is belt-and-braces: the two checks could drift, and substituting a
        // catalogue entry would silently re-scale every later IOB calculation.
        val iCfg = profileFunction.getRunningOrRequestedICfg()
            ?: return pumpEnactResultProvider().success(false).comment(CoreUiStrings.profile_switch_no_insulin)
        val profileStore = profileRepository.profile.value
            ?: return pumpEnactResultProvider().success(false).comment(CoreUiStrings.noprofile)
        if (profileStore.getSpecificProfile(inputProfileName.value) == null) {
            aapsLogger.error(LTag.AUTOMATION, "Selected profile does not exist! - ${inputProfileName.value}")
            return pumpEnactResultProvider().success(false).comment(CoreUiStrings.notexists)
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
        return pumpEnactResultProvider().success(result != null).comment(CoreUiStrings.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionProfileSwitch::class.simpleName)
            put("data", buildJsonObject { put("profileToSwitchTo", inputProfileName.value) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        inputProfileName.value = o.lenientString("profileToSwitchTo", "")
        return this
    }

    override fun isValid(): Boolean = profileRepository.profile.value?.getSpecificProfile(inputProfileName.value) != null
}