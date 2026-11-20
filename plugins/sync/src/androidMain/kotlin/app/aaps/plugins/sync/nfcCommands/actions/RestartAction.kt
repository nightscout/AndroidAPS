package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcAaps
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class RestartAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val configBuilder: ConfigBuilder
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_restart_aaps
    override val elementType = ElementType.AAPS
    override val argType = listOf<ArgType>()
    override val icon = IcAaps

    override suspend fun execute(tagName: String): NfcExecutionResult {
        uel.log(
            action = Action.EXIT_AAPS,
            source = source,
            note = tagName
        )
        configBuilder.exitApp("NFC", Sources.NfcCommands, true)
        return NfcExecutionResult(true, rh.gs(SyncStrings.nfccommands_restarting))
    }
}
