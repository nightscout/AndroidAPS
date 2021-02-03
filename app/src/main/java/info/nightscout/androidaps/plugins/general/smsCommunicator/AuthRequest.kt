package info.nightscout.androidaps.plugins.general.smsCommunicator

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePassword
import info.nightscout.androidaps.plugins.general.smsCommunicator.otp.OneTimePasswordValidationResult
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

    private val date = DateUtil.now()
    private var processed = false

    init {
        injector.androidInjector().inject(this)
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
        if (DateUtil.now() - date < Constants.SMS_CONFIRM_TIMEOUT) {
            processed = true
            aapsLogger.debug(LTag.SMS, "Processing confirmed SMS: " + requester.text)
            action.run()
            return
        }
        aapsLogger.debug(LTag.SMS, "Timed out SMS: " + requester.text)
    }
}