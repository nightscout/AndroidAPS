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
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcExecutionResult
import java.util.concurrent.TimeUnit
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR
import app.aaps.plugins.sync.nfcCommands.NfcParams
import app.aaps.plugins.sync.nfcCommands.NfcRuntimeState

class BolusAction(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    uel: UserEntryLogger,
    private val bolusProgressData: BolusProgressData,
    private val commandQueue: CommandQueue,
    private val constraintChecker: ConstraintsChecker,
    private val dateUtil: DateUtil,
    private val loop: Loop,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val runtimeState: NfcRuntimeState
) : NfcAction(aapsLogger, rh, uel) {
    @StringRes override val labelResId = InterfacesR.string.bolus
    override val elementType = ElementType.INSULIN
    override val argType = listOf(ArgType.INSULIN, ArgType.MEAL_CHECK)
    override val icon
        get() = elementType.icon()

    override val secondaryIcon: ImageVector?
        get() = if (params.isMeal) IcTtEatingSoon else null

    override val secondaryIconColor: (@Composable () -> Color)?
        get() = if (params.isMeal) {
            @Composable { ElementType.TEMP_TARGET_MANAGEMENT.color() }
        } else null

    override suspend fun getDefaultParams() = NfcParams(insulin = NfcDefaults.BOLUS_INSULIN)

    override suspend fun formatParams(tagName: String): String {
        val amount = (params.insulin ?: NfcDefaults.BOLUS_INSULIN)
        val isMeal = params.isMeal
        val base = rh.gs(CoreUiR.string.goingtodeliver, amount)
        return if (isMeal) {
            rh.gs(CoreUiR.string.text_with_detail, base, rh.gs(CoreUiR.string.eatingsoon))
        } else {
            base
        }
    }

    override suspend fun execute(tagName: String): NfcExecutionResult {
        if (commandQueue.bolusInQueue()) {
            return NfcExecutionResult(false, rh.gs(R.string.nfccommands_another_bolus_in_queue))
        }
        if (dateUtil.now() - runtimeState.lastRemoteBolusTime < Constants.REMOTE_BOLUS_MIN_DISTANCE) {
            return NfcExecutionResult(false, rh.gs(R.string.nfccommands_remote_bolus_not_allowed))
        }
        if (loop.runningMode().pausesLoopExecution()) {
            return NfcExecutionResult(false, rh.gs(InterfacesR.string.pumpsuspended))
        }
        
        var bolus = params.insulin ?: return invalidFormat()
        val isMeal = params.isMeal
        
        if (bolus <= 0.0) return invalidFormat()
        
        bolus = constraintChecker.applyBolusConstraints(ConstraintObject(bolus, aapsLogger)).value()
        
        val detailedBolusInfo = DetailedBolusInfo().apply { insulin = bolus }
        val result = commandQueue.bolus(detailedBolusInfo)
        
        val userStop = bolusProgressData.isStopPressed
        if (!result.success && !userStop) {
            aapsLogger.error(LTag.NFC, "bolus failed: ${result.comment}")
            return commandNotPossible()
        }

        val delivered = result.bolusDelivered
        uel.log(
            action = if (isMeal) Action.TREATMENT else Action.BOLUS,
            source = source,
            note = tagName,
            listValues = listOf(
                ValueWithUnit.Insulin(delivered)
            )
        )

        runtimeState.lastRemoteBolusTime = dateUtil.now()
        
        if (isMeal && !userStop) {
            profileFunction.getProfile()?.let {
                val eatingSoonTTDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                val eatingSoonTT = profileUtil.fromMgdlToUnits(preferences.ttTargetMgdl(TT.Reason.EATING_SOON), profileUtil.units)
                persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                    temporaryTarget = TT(
                        timestamp = dateUtil.now(),
                        duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                        reason = TT.Reason.EATING_SOON,
                        lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                        highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units),
                    ),
                    action = Action.TT,
                    source = Sources.NfcCommands,
                    note = null,
                    listValues = listOf(
                        ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                        ValueWithUnit.Mgdl(profileUtil.convertToMgdl(eatingSoonTT, profileUtil.units)),
                        ValueWithUnit.Minute(eatingSoonTTDuration),
                    ),
                )
            }
        }
        
        val resId = if (userStop) CoreUiR.string.stop_pressed
        else if (isMeal) R.string.smscommunicator_meal_bolus_delivered
        else R.string.smscommunicator_bolus_delivered
        
        return NfcExecutionResult(true, rh.gs(resId, delivered))
    }
}
