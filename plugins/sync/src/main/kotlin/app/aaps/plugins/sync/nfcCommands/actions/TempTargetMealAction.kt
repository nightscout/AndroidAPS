package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import app.aaps.plugins.sync.R
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR

class TempTargetMealAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = CoreUiR.string.eatingsoon
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf<ArgType>()
    override val icon = IcTtEatingSoon
    override val customIconColor: @Composable () -> Color = { AapsTheme.elementColors.tempTarget }

    override suspend fun formatParams(): String {
        val units = profileUtil.units
        val ttDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
        val tt = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.EATING_SOON), profileUtil.units)
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        return "$ttString $unitLabel, ${ttDuration}min"
    }

    override suspend fun execute(): NfcExecutionResult {
        val units = profileUtil.units
        val ttDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
        val tt = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.EATING_SOON), profileUtil.units)
        val reason = TT.Reason.EATING_SOON

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
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            listValues = listOf(
                ValueWithUnit.TETTReason(reason),
                ValueWithUnit.fromGlucoseUnit(tt, units),
                ValueWithUnit.Minute(ttDuration),
            ),
        )
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(tt) else decimalFormatter.to0Decimal(tt)
        return NfcExecutionResult(true, rh.gs(R.string.nfccommands_tt_set, ttString, ttDuration))
    }
}
