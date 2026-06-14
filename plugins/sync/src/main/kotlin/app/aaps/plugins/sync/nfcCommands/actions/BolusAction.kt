package app.aaps.plugins.sync.nfcCommands.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import app.aaps.plugins.sync.nfcCommands.NfcJsonKeys
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import app.aaps.core.ui.R as CoreUiR

class BolusAction(plugin: NfcCommandsPlugin) : NfcAction(plugin) {
    @StringRes override val labelResId = CoreUiR.string.bolus
    override val elementType = ElementType.INSULIN
    override val argType = listOf(ArgType.INSULIN, ArgType.MEAL_CHECK)
    override val icon
        get() = elementType.icon()

    override val secondaryIcon: ImageVector?
        get() = if (params.optBoolean(NfcJsonKeys.IS_MEAL, false)) IcTtEatingSoon else null

    override val secondaryIconColor: (@Composable () -> Color)?
        get() = if (params.optBoolean(NfcJsonKeys.IS_MEAL, false)) {
            @Composable { ElementType.TEMP_TARGET_MANAGEMENT.color() }
        } else null

    override suspend fun getDefaultParams(): JSONObject = 
        JSONObject().put(NfcJsonKeys.AMOUNT, 0.0).put(NfcJsonKeys.IS_MEAL, false)

    override suspend fun formatParams(): String {
        val amount = params.optDouble(NfcJsonKeys.AMOUNT, 0.0)
        val isMeal = params.optBoolean(NfcJsonKeys.IS_MEAL, false)
        val base = plugin.rh.gs(CoreUiR.string.goingtodeliver, amount)
        return if (isMeal) {
            "$base (${plugin.rh.gs(CoreUiR.string.eatingsoon)})"
        } else {
            base
        }
    }

    override suspend fun execute(): NfcExecutionResult {
        if (plugin.commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (plugin.dateUtil.now() - plugin.lastRemoteBolusTime < Constants.remoteBolusMinDistance) {
            return NfcExecutionResult(false, plugin.rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (plugin.loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, plugin.rh.gs(CoreUiR.string.pumpsuspended))
        }
        
        var bolus = params.optDouble(NfcJsonKeys.AMOUNT, 0.0)
        val isMeal = params.optBoolean(NfcJsonKeys.IS_MEAL, false)
        
        if (bolus <= 0.0) return invalidFormat()
        
        bolus = plugin.constraintChecker.applyBolusConstraints(ConstraintObject(bolus, plugin.aapsLogger)).value()
        
        val detailedBolusInfo = DetailedBolusInfo().apply { insulin = bolus }
        val result = plugin.commandQueue.bolus(detailedBolusInfo)
        if (!result.success) {
            plugin.aapsLogger.error(LTag.NFC, "bolus failed: ${result.comment}")
            return commandNotPossible()
        }
        uel.log(
            action = if (isMeal) Action.TREATMENT else Action.BOLUS,
            source = source,
            note = params.optString(NfcJsonKeys.TAG_NAME, ""),
            listValues = listOf(
                ValueWithUnit.Insulin(bolus)
            )
        )

        plugin.setLastRemoteBolusTime(plugin.dateUtil.now())
        
        if (isMeal) {
            plugin.profileFunction.getProfile()?.let {
                val eatingSoonTTDuration = plugin.preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                val eatingSoonTT = plugin.profileUtil.fromMgdlToUnits(plugin.preferences.ttTargetMgdl(TT.Reason.EATING_SOON), plugin.profileUtil.units)
                plugin.persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = TT(
                        timestamp = plugin.dateUtil.now(),
                        duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                        reason = TT.Reason.EATING_SOON,
                        lowTarget = plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units),
                        highTarget = plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units),
                    ),
                    action = Action.TT,
                    source = Sources.NfcCommands,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                        ValueWithUnit.Mgdl(plugin.profileUtil.convertToMgdl(eatingSoonTT, plugin.profileUtil.units)),
                        ValueWithUnit.Minute(eatingSoonTTDuration),
                    ),
                )
            }
        }
        
        val resId = if (isMeal) R.string.smscommunicator_meal_bolus_delivered else R.string.smscommunicator_bolus_delivered
        return NfcExecutionResult(true, plugin.rh.gs(resId, bolus))
    }
}
