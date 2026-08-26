package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.core.ui.R as CoreUiR

class BasalCancelAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val commandQueue: CommandQueue
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = CoreUiR.string.cancel_temp
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
            NfcExecutionResult(true, rh.gs(CoreUiR.string.stoptemptarget))
        } else {
            aapsLogger.error(LTag.NFC, "cancelTempBasal failed: ${result.comment}")
            commandNotPossible()
        }
    }
}
