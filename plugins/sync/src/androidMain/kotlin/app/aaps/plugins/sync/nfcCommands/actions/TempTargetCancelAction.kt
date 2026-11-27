package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtCancel
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult

class TempTargetCancelAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.stoptemptarget
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf<ArgType>()
    override val icon = IcTtCancel
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.loopDisabled }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        persistenceLayer.cancelCurrentTemporaryTargetIfAny(
            timestamp = dateUtil.now(),
            action = Action.CANCEL_TT,
            source = source,
            note = rh.gs(SyncStrings.nfccommands_tt_canceled),
            listValues = listOf(ValueWithUnit.SimpleString(rh.gsNotLocalised(SyncStrings.nfccommands_tt_canceled))),
        )
        uel.log(
            action = Action.CANCEL_TT,
            source = source,
            note = tagName
        )
        return NfcExecutionResult(true, rh.gs(SyncStrings.nfccommands_tt_canceled))
    }
}
