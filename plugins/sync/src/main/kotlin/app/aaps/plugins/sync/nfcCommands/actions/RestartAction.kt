package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R

class RestartAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val configBuilder: ConfigBuilder
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_restart_aaps
    override val elementType = ElementType.AAPS
    override val argType = listOf<ArgType>()
    override val icon = IcAaps

    override suspend fun execute(): NfcExecutionResult {
        uel.log(
            action = Action.EXIT_AAPS,
            source = source,
            note = params.optString(NfcJsonKeys.TAG_NAME, "")
        )
        configBuilder.exitApp("NFC", Sources.NfcCommands, true)
        return NfcExecutionResult(true, rh.gs(R.string.nfccommands_restarting))
    }
}
