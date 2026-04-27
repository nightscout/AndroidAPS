package app.aaps.plugins.sync.smsCommunicator

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.sync.smsCommunicator.otp.OneTimePasswordValidationResult
import kotlinx.coroutines.delay

/**
 * Tracks a single pending SMS confirmation flow: sends the request prompt on
 * construction and runs [action] when the matching OTP arrives within the
 * 5-minute window. Pure value object — construct via [SmsCommunicatorPlugin]'s
 * `authRequest(...)` factory at the call site, no DI needed.
 */
class AuthRequest(
    val requester: Sms, // exposed: SmsCommunicatorPlugin reads requester.phoneNumber to route the reply
    requestText: String,
    val confirmCode: String, // exposed: tests read it from messageToConfirm to mint a confirmation reply
    private val action: SmsAction,
    private val aapsLogger: AAPSLogger,
    private val smsCommunicator: SmsCommunicator,
    private val rh: ResourceHelper,
    private val otp: OneTimePassword,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue
) {

    private val date = dateUtil.now()
    private var processed = false

    init {
        smsCommunicator.sendSMS(Sms(requester.phoneNumber, requestText))
    }

    private fun codeIsValid(toValidate: String): Boolean =
        otp.checkOTP(toValidate) == OneTimePasswordValidationResult.OK

    suspend fun action(codeReceived: String) {
        if (processed) {
            aapsLogger.debug(LTag.SMS, "Already processed")
            return
        }
        if (!codeIsValid(codeReceived)) {
            processed = true
            aapsLogger.debug(LTag.SMS, "Wrong code")
            smsCommunicator.sendSMS(Sms(requester.phoneNumber, rh.gs(R.string.sms_wrong_code)))
            return
        }
        if (dateUtil.now() - date < Constants.SMS_CONFIRM_TIMEOUT) {
            processed = true
            if (action.pumpCommand) {
                val start = dateUtil.now()
                //wait for empty queue
                while (start + T.mins(3).msecs() > dateUtil.now()) {
                    if (commandQueue.size() == 0) break
                    delay(100)
                }
                if (commandQueue.size() != 0) {
                    aapsLogger.debug(LTag.SMS, "Command timed out: " + requester.text)
                    smsCommunicator.sendSMS(Sms(requester.phoneNumber, rh.gs(R.string.sms_timeout_while_waiting)))
                    return
                }
            }
            aapsLogger.debug(LTag.SMS, "Processing confirmed SMS: " + requester.text)
            action.run()
            return
        }
        aapsLogger.debug(LTag.SMS, "Timed out SMS: " + requester.text)
    }
}
