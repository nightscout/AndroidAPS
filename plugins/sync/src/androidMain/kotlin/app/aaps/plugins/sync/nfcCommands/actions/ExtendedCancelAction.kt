package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcCancelExtendedBolus
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class ExtendedCancelAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = SyncStrings.nfccommands_cmd_extended_stop
    override val elementType = ElementType.EXTENDED_BOLUS
    override val argType = listOf<ArgType>()
    override val icon = IcCancelExtendedBolus

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val result = commandQueue.cancelExtended()
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_EXTENDED_BOLUS,
                source = source,
                note = tagName,
            )
            NfcExecutionResult(true, rh.gs(SyncStrings.nfccommands_extended_canceled))
        } else {
            aapsLogger.error(LTag.NFC, "cancelExtended failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
