package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/**
 * Executes a remote bolus delivery: BOLUS <insulin> [MEAL].
 *
 * Delivered through [WizardBolusExecutor], so the bolus is capped, gated and logged exactly like one from the phone.
 * MEAL adds the eating-soon temp target to the same batch. As in the phone's insulin dialog, the target is set once
 * the bolus is accepted, not after the pump finished.
 */
class BolusAction(
    private val insulin: Double,
    private val isMeal: Boolean,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val commandQueue: CommandQueue,
    private val rh: TextResolver,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val smsCommunicator: SmsCommunicator,
    private val bolusProgressData: BolusProgressData,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String,
    private val updateLastRemoteBolusTime: (Long) -> Unit
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val mealTarget = if (isMeal) {
            val ttMgdl = preferences.ttTargetMgdl(TT.Reason.EATING_SOON)
            BatchAction.TempTarget(
                reason = TT.Reason.EATING_SOON.text, lowMgdl = ttMgdl, highMgdl = ttMgdl,
                durationMinutes = preferences.ttDurationMinutes(TT.Reason.EATING_SOON), startOffsetMinutes = 0
            )
        } else null
        val bolus = BatchAction.Bolus(
            insulin = insulin, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
            recordOnly = false, notes = "", timestamp = 0L, iCfg = null
        )
        val actions = listOfNotNull(bolus, mealTarget)
        when (val result = wizardBolusExecutor.runSmsBatch(actions, waitForDose = true)) {
            is SmsBatchResult.Done    -> {
                commandQueue.readStatus(rh.gs(CoreUiStrings.sms))
                updateLastRemoteBolusTime(dateUtil.now())
                // After STOP the pump gave less than this, and the executor does not report how much. The status
                // below shows what the pump has, so the reply does not name an amount it cannot vouch for.
                var replyText = when {
                    bolusProgressData.isStopPressed -> rh.gs(CoreUiStrings.stop_pressed)
                    isMeal                          -> rh.gs(SyncStrings.smscommunicator_meal_bolus_delivered, result.preview.insulin)
                    else                            -> rh.gs(SyncStrings.smscommunicator_bolus_delivered, result.preview.insulin)
                }
                replyText += "\n" + shortStatusBlocking()
                if (mealTarget != null) {
                    val units = profileUtil.units
                    val ttDisplay = profileUtil.fromMgdlToUnits(mealTarget.lowMgdl, units)
                    val tt = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(ttDisplay) else decimalFormatter.to0Decimal(ttDisplay)
                    replyText += "\n" + rh.gs(SyncStrings.smscommunicator_meal_bolus_delivered_tt, tt, mealTarget.durationMinutes)
                }
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
            }

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_bolus_failed), shortStatusBlocking)))
        }
    }
}
