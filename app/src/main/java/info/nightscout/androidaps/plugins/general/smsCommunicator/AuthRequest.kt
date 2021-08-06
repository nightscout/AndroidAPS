package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class AuthRequest internal constructor(
    injector: HasAndroidInjector,
    var requester: Sms,
    requestText: String,
    var confirmCode: String,
    val action: SmsAction) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var otp: OneTimePassword
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var commandQueue: CommandQueueProvider

    private var date = 0L
    private var processed = false

    init {
        injector.androidInjector().inject(this)
        date = dateUtil.now()
        smsCommunicatorPlugin.sendSMS(Sms(requester.phoneNumber, requestText))
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
            smsCommunicatorPlugin.sendSMS(Sms(requester.phoneNumber, resourceHelper.gs(R.string.sms_wrongcode)))
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
                    smsCommunicatorPlugin.sendSMS(Sms(requester.phoneNumber, resourceHelper.gs(R.string.sms_timeout_while_wating)))
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