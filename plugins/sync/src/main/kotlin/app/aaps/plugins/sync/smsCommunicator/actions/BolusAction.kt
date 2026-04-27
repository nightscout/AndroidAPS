package app.aaps.plugins.sync.smsCommunicator.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.SmsAction
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

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
    private val sendSMSToAllNumbers: (Sms) -> Unit,
    private val shortStatusBlocking: () -> String,
    private val updateLastRemoteBolusTime: (Long) -> Unit
) : SmsAction(pumpCommand = true) {

    override suspend fun run() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = insulin
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                val resultSuccess = result.success
                val resultBolusDelivered = result.bolusDelivered
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.sms), object : Callback() {
                    override fun run() {
                        if (resultSuccess) {
                            var replyText = if (isMeal)
                                rh.gs(R.string.smscommunicator_meal_bolus_delivered, resultBolusDelivered)
                            else
                                rh.gs(R.string.smscommunicator_bolus_delivered, resultBolusDelivered)
                            replyText += "\n" + shortStatusBlocking()
                            updateLastRemoteBolusTime(dateUtil.now())
                            if (isMeal) {
                                runBlocking { profileFunction.getProfile() }?.let { currentProfile ->
                                    val eatingSoonTTDuration = preferences.ttDurationMinutes(TT.Reason.EATING_SOON)
                                    val eatingSoonTTMgdl = preferences.ttTargetMgdl(TT.Reason.EATING_SOON)
                                    // Await the TT insert so the reply SMS only goes out if persistence succeeded.
                                    // We're inside Callback.run() (sync); runBlocking on the command-queue HandlerThread is acceptable.
                                    runBlocking {
                                        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                                            temporaryTarget = TT(
                                                timestamp = dateUtil.now(),
                                                duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
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
                                    }
                                    val eatingSoonTTDisplay = profileUtil.fromMgdlToUnits(eatingSoonTTMgdl, currentProfile.units)
                                    val tt = if (currentProfile.units == GlucoseUnit.MMOL) {
                                        decimalFormatter.to1Decimal(eatingSoonTTDisplay)
                                    } else decimalFormatter.to0Decimal(eatingSoonTTDisplay)
                                    replyText += "\n" + rh.gs(R.string.smscommunicator_meal_bolus_delivered_tt, tt, eatingSoonTTDuration)
                                }
                            }
                            sendSMSToAllNumbers(Sms(receivedSms.phoneNumber, replyText))
                            uel.log(Action.BOLUS, Sources.SMS, replyText)
                        } else {
                            // Read pump status once and reuse for both the SMS reply and the UEL log.
                            val status = shortStatusBlocking()
                            val replyText = rh.gs(R.string.smscommunicator_bolus_failed) + "\n" + status
                            smsCommunicator.sendSMS(Sms(receivedSms.phoneNumber, replyText))
                            uel.log(
                                Action.BOLUS, Sources.SMS, status + "\n" + rh.gs(R.string.smscommunicator_bolus_failed),
                                ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.smscommunicator_bolus_failed))
                            )
                        }
                    }
                })
            }
        })
    }
}
