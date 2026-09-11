package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcParams

class ProfileSwitchAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.careportal_profileswitch
    override val elementType = ElementType.PROFILE_MANAGEMENT
    override val argType = listOf(ArgType.PROFILE_NAME, ArgType.PERCENT)
    override val icon
        get() = elementType.icon()
    override val customIconColor: @Composable () -> Color = {
        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White
    }

    override suspend fun getDefaultParams() =
        NfcParams(
            profileName = profileFunction.getOriginalProfileName(),
            percent = NfcDefaults.PROFILE_SWITCH_PERCENT
        )

    override suspend fun formatParams(tagName: String): String? {
        val profileName = (params.profileName ?: "")
        val percentage = (params.percent ?: NfcDefaults.PROFILE_SWITCH_PERCENT)
        return if (percentage == 100) profileName else "$profileName $percentage%"
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val profileName = params.profileName ?: return invalidFormat()
        if (profileName.isNullOrBlank()) return invalidFormat()
        val percentage = (params.percent ?: return invalidFormat())
            .coerceIn(NfcDefaults.PROFILE_SWITCH_PERCENT_RANGE)
        
        val profileStore = profileRepository.profile.value ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.notconfigured))
        
        val iCfg = profileFunction.getRunningOrRequestedICfg()
            ?: return NfcExecutionResult(false, rh.gs(CoreUiStrings.profile_switch_no_insulin))

        val created = profileFunction.createProfileSwitch(
            profileStore = profileStore,
            profileName = profileName,
            durationInMinutes = 0,
            percentage = percentage,
            timeShiftInHours = 0,
            timestamp = dateUtil.now(),
            action = Action.PROFILE_SWITCH,
            source = source,
            note = rh.gs(SyncStrings.nfccommands_profile_switch_created),
            listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(SyncStrings.nfccommands_profile_switch_created))),
            iCfg = iCfg,
        )
        return if (created != null) {
            val resultMessage = if (percentage == 100) profileName else "$profileName $percentage%"
            uel.log(
                action = Action.PROFILE_SWITCH,
                source = source,
                note = tagName,
                listValues = listOf(
                    ValueWithUnit.SimpleString(profileName),
                    ValueWithUnit.Percent(percentage)
                )
            )
            NfcExecutionResult(true, resultMessage)
        } else {
            NfcExecutionResult(false, rh.gs(CoreUiStrings.invalid_profile))
        }
    }
}
