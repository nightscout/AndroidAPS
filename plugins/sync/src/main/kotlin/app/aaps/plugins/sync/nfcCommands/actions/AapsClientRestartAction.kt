package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType

class AapsClientRestartAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val activePlugin: ActivePlugin
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_aapsclient_restart
    override val elementType = ElementType.AAPS
    override val argType = listOf<ArgType>()
    override val icon = IcAaps

    override suspend fun execute(tagName: String): NfcExecutionResult {
        activePlugin.getSpecificPluginsListByInterface(NsClient::class).forEach {
            (it as? NsClient)?.resend("NFC")
        }
        uel.log(
            action = Action.START_AAPS,
            note = tagName,
            source = source,
        )
        return NfcExecutionResult(true, rh.gs(R.string.nfccommands_aapsclient_restart_sent))
    }
}
