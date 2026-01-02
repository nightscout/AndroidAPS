package app.aaps.plugins.main.general.smsCommunicator

import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.Sms
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePassword
import app.aaps.plugins.main.general.smsCommunicator.otp.OneTimePasswordValidationResult
import jakarta.inject.Inject

class AuthRequest @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val smsCommunicator: SmsCommunicator,
    private val rh: ResourceHelper,
    private val otp: OneTimePassword,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue
) {

    private var date = dateUtil.now()
    private var processed = false
    lateinit var requester: Sms
    lateinit var requestText: String
    lateinit var confirmCode: String
    lateinit var action: SmsAction

    fun with(requester: Sms, requestText: String, confirmCode: String, action: SmsAction): AuthRequest {
        this.requester = requester
        this.requestText = requestText
        this.confirmCode = confirmCode
        this.action = action
        smsCommunicator.sendSMS(Sms(requester.phoneNumber, requestText))
        return this
    }

    private fun codeIsValid(toValidate: String): Boolean =
        otp.checkOTP(toValidate) == OneTimePasswordValidationResult.OK

    fun action(codeReceived: String) {
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
                    SystemClock.sleep(100)
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