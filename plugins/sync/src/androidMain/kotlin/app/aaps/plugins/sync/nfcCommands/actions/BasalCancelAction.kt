package app.aaps.plugins.sync.nfcCommands.actions

import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class BasalCancelAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.cancel_temp
    override val elementType = ElementType.TEMP_BASAL
    override val argType = listOf<ArgType>()
    override val icon = IcTbrCancel

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val result = commandQueue.cancelTempBasal(enforceNew = true)
        return if (result.success) {
            uel.log(
                action = Action.CANCEL_TEMP_BASAL,
                note = tagName,
                source = source,
            )
            NfcExecutionResult(true, rh.gs(CoreUiStrings.stoptemptarget))
        } else {
            aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
