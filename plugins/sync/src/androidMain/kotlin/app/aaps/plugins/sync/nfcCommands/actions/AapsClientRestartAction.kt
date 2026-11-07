package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class AapsClientRestartAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val activePlugin: ActivePlugin
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_aapsclient_restart
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
        return NfcExecutionResult(true, rh.gs(SyncStrings.nfccommands_aapsclient_restart_sent))
    }
}
