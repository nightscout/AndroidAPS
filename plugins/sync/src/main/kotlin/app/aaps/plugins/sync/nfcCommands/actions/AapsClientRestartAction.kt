package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType

class AapsClientRestartAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = R.string.nfccommands_cmd_aapsclient_restart
    override val elementType = ElementType.AAPS
    override val argType = listOf<ArgType>()
    override val icon = IcAaps

    override suspend fun execute(): NfcExecutionResult {
        plugin.activePlugin.getSpecificPluginsListByInterface(NsClient::class.java).forEach {
            (it as? NsClient)?.resend("NFC")
        }
        uel.log(
            action = Action.START_AAPS,
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            source = source,
        )
        return NfcExecutionResult(true, plugin.rh.gs(R.string.nfccommands_aapsclient_restart_sent))
    }
}
