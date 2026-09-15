package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import kotlin.time.Duration.Companion.minutes

/** Executes a remote bolus delivery: BOLUS <insulin> [MEAL]. */
class BolusAction(
    private val insulin: Double,
    private val isMeal: Boolean,
    private val receivedSms: Sms,
    private val commandQueue: CommandQueue,
    private val rh: ResourceHelper,
    private val uel: UserEntryLogger,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val smsCommunicator: SmsCommunicator,
    private val bolusProgressData: BolusProgressData,
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String,
    private val updateLastRemoteBolusTime: (Long) -> Unit
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = insulin
        val bolusResult = commandQueue.bolus(detailedBolusInfo)
        val resultSuccess = bolusResult.success || bolusProgressData.isStopPressed
        val resultBolusDelivered = bolusResult.bolusDelivered
        commandQueue.readStatus(rh.gs(CoreUiStrings.sms))
        if (resultSuccess) {
            var replyText = if (isMeal)
                rh.gs(SyncStrings.smscommunicator_meal_bolus_delivered, resultBolusDelivered)
            else
                rh.gs(SyncStrings.smscommunicator_bolus_delivered, resultBolusDelivered)
            if (bolusProgressData.isStopPressed) {
                replyText = rh.gs(CoreUiStrings.stop_pressed) + " " + replyText
            }
            replyText += "\n" + shortStatusBlocking()
            updateLastRemoteBolusTime(dateUtil.now())
            if (isMeal) {
                profileFunction.getProfile()?.let { currentProfile ->
                    val eatingSoonTTDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                    val eatingSoonTTMgdl = preferences.ttTargetMgdl(TT.Reason.EATING_SOON)
                    persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                        temporaryTarget = TT(
                            timestamp = dateUtil.now(),
                            duration = eatingSoonTTDuration.toLong().minutes.inWholeMilliseconds,
                            reason = TT.Reason.EATING_SOON,
                            lowTarget = eatingSoonTTMgdl,
                            highTarget = eatingSoonTTMgdl
                        ),
                        action = Action.TT,
                        source = Sources.SMS,
                        note = null,
                        listValues = listOf(
                            ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                            ValueWithUnit.Mgdl(eatingSoonTTMgdl),
                            ValueWithUnit.Minute(eatingSoonTTDuration)
                        )
                    )
                    val eatingSoonTTDisplay = profileUtil.fromMgdlToUnits(eatingSoonTTMgdl, currentProfile.units)
                    val tt = if (currentProfile.units == GlucoseUnit.MMOL) {
                        decimalFormatter.to1Decimal(eatingSoonTTDisplay)
                    } else decimalFormatter.to0Decimal(eatingSoonTTDisplay)
                    replyText += "\n" + rh.gs(SyncStrings.smscommunicator_meal_bolus_delivered_tt, tt, eatingSoonTTDuration)
                }
            }
            sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
            uel.log(Action.BOLUS, Sources.SMS, replyText)
        } else {
            val status = shortStatusBlocking()
            val replyText = rh.gs(SyncStrings.smscommunicator_bolus_failed) + "\n" + status
            smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
            uel.log(
                Action.BOLUS, Sources.SMS, status + "\n" + rh.gs(SyncStrings.smscommunicator_bolus_failed),
                ValueWithUnit.SimpleString(rh.gsNotLocalised(SyncStrings.smscommunicator_bolus_failed))
            )
        }
    }
}
