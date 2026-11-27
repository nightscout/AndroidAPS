package app.aaps.plugins.sync.nfcCommands.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import java.util.concurrent.TimeUnit

class TempTargetActivityAction(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil
) : NfcAction(aapsLogger, rh, uel) {
    override val label: TextRef = CoreUiStrings.activity
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf<ArgType>()
    override val icon = IcTtActivity
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.exercise }

    override suspend fun formatParams(tagName: String): String {
        val units = profileUtil.units
        val ttDuration = preferences.ttDurationMinutes(TT.Reason.ACTIVITY)
        val tt = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.ACTIVITY), profileUtil.units)
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        return "$ttString $unitLabel, ${ttDuration}min"
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val units = profileUtil.units
        val ttDuration = preferences.ttDurationMinutes(TT.Reason.ACTIVITY)
        val tt = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.ACTIVITY), profileUtil.units)
        val reason = TT.Reason.ACTIVITY

        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = dateUtil.now(),
                duration = TimeUnit.MINUTES.toMillis(ttDuration.toLong()),
                reason = reason,
                lowTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
                highTarget = profileUtil.convertToMgdl(tt, profileUtil.units),
            ),
            action = Action.TT,
            source = source,
            note = tagName,
            listValues = listOf(
                ValueWithUnit.TETTReason(reason),
                ValueWithUnit.fromGlucoseUnit(tt, units),
                ValueWithUnit.Minute(ttDuration),
            ),
        )
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
        return NfcExecutionResult(true, rh.gs(SyncStrings.nfccommands_tt_set, ttString, ttDuration))
    }
}
