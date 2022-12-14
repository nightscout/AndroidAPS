package info.nightscout.plugins.general.smsCommunicator

import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.smsCommunicator.Sms
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.plugins.R
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject

class AuthRequest(
    injector: HasAndroidInjector,
    var requester: Sms,
    requestText: String,
    var confirmCode: String,
    val action: SmsAction
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var commandQueue: CommandQueue

    private var date = 0L
    private var processed = false

    init {
        injector.androidInjector().inject(this)
        date = dateUtil.now()
        smsCommunicator.sendSMS(Sms(requester.phoneNumber, requestText))
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