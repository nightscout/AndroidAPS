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
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR

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
    
    override suspend fun getDefaultParams(): JSONObject = JSONObject().apply {
        val units = profileUtil.units
        val profile = profileFunction.getProfile()
        val defaultTargetMgdl = profile?.getTargetLowMgdl() ?: 100.0
        val defaultTarget = profileUtil.fromMgdlToUnits(defaultTargetMgdl, units)
        
        put(NfcJsonKeys.GLUCOSE, defaultTarget)
        put(NfcJsonKeys.DURATION, 60)
    }

    override suspend fun formatParams(): String {
        val units = profileUtil.units
        val glucose = params.optDouble(NfcJsonKeys.GLUCOSE, 0.0)
        val duration = params.optInt(NfcJsonKeys.DURATION, 0)
        val unitLabel = if (units == GlucoseUnit.MMOL) "mmol/l" else "mg/dl"
        val glucoseString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(glucose) else decimalFormatter.to0Decimal(glucose)
        return "$glucoseString $unitLabel, ${duration}min"
    }

    override suspend fun execute(): NfcExecutionResult {
        val units = profileUtil.units
        val glucose = params.optDouble(NfcJsonKeys.GLUCOSE, 0.0)
        val durationMinutes = params.optInt(NfcJsonKeys.DURATION, 60)

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
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
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
