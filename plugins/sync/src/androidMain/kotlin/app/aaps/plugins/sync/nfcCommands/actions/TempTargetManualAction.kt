package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams

class TempTargetManualAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = CoreUiR.string.custom
    override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    override val argType = listOf(ArgType.GLUCOSE_TARGET, ArgType.DURATION)
    override val icon = IcTtManual
    
    override suspend fun getDefaultParams(): NfcParams {
        val defaultTargetMgdl =
            profileFunction.getProfile()?.getTargetLowMgdl() ?: NfcDefaults.TEMP_TARGET_MGDL_WITHOUT_PROFILE
        return NfcParams(
            glucose = profileUtil.fromMgdlToUnits(defaultTargetMgdl, profileUtil.units),
            duration = NfcDefaults.TEMP_TARGET_DURATION_MINUTES
        )
    }

    override suspend fun formatParams(tagName: String): String {
        val units = profileUtil.units
        val glucose = (params.glucose ?: NfcDefaults.GLUCOSE_TARGET_UNSET)
        val duration = (params.duration ?: NfcDefaults.TEMP_TARGET_DURATION_MINUTES)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        val glucoseString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(glucose) else decimalFormatter.to0Decimal(glucose)
        return "$glucoseString $unitLabel, ${duration}min"
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        val units = profileUtil.units
        val glucose = params.glucose ?: return invalidFormat()
        val durationMinutes = params.duration ?: return invalidFormat()

        if (glucose <= 0.0 || durationMinutes <= 0) return invalidFormat()

        val targetMgdl = profileUtil.convertToMgdl(glucose, units)
        val reason = TT.Reason.CUSTOM

        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
            temporaryTarget = TT(
                timestamp = dateUtil.now(),
                duration = TimeUnit.MINUTES.toMillis(durationMinutes.toLong()),
                reason = reason,
                lowTarget = targetMgdl,
                highTarget = targetMgdl,
            ),
            action = Action.TT,
            source = source,
            note = tagName,
            listValues = listOf(
                ValueWithUnit.TETTReason(reason),
                ValueWithUnit.fromGlucoseUnit(glucose, units),
                ValueWithUnit.Minute(durationMinutes),
            ),
        )
        
        val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(glucose) else decimalFormatter.to0Decimal(glucose)
        return NfcExecutionResult(true, rh.gs(R.string.nfccommands_tt_set, ttString, durationMinutes))
    }
}
