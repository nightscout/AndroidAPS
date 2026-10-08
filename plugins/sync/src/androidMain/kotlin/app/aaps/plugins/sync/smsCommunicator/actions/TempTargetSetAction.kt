package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import app.aaps.plugins.sync.smsCommunicator.SmsBatchResult
import app.aaps.plugins.sync.smsCommunicator.runSmsBatch

/** Activates a preset temp target through [WizardBolusExecutor]: TARGET MEAL/ACTIVITY/HYPO. */
class TempTargetSetAction(
    private val reason: TT.Reason,
    private val receivedSms: Sms,
    private val wizardBolusExecutor: WizardBolusExecutor,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val decimalFormatter: DecimalFormatter,
    private val rh: TextResolver,
    private val smsCommunicator: SmsCommunicator,
    private val sendSMSToAllNumbers: (Sms) -> Unit
) : SmsAction(pumpCommand = false) {

    override suspend fun run() {
        val ttDuration = preferences.ttDurationMinutes(reason)
        val ttMgdl = preferences.ttTargetMgdl(reason)
        val target = BatchAction.TempTarget(reason = reason.text, lowMgdl = ttMgdl, highMgdl = ttMgdl, durationMinutes = ttDuration, startOffsetMinutes = 0)
        when (val result = wizardBolusExecutor.runSmsBatch(listOf(target))) {
            is SmsBatchResult.Done    -> {
                val units = profileUtil.units
                val ttDisplay = profileUtil.fromMgdlToUnits(ttMgdl, units)
                val ttString = if (units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(ttDisplay) else decimalFormatter.to0Decimal(ttDisplay)
                sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, rh.gs(SyncStrings.smscommunicator_tt_set, ttString, ttDuration)))
            }

            is SmsBatchResult.NotDone ->
                smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, result.reply(rh.gs(SyncStrings.smscommunicator_remote_command_not_possible))))
        }
    }
}
