package info.nightscout.androidaps.plugins.general.smsCommunicator

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.utils.DateUtil
import org.slf4j.LoggerFactory

class AuthRequest internal constructor(val plugin: SmsCommunicatorPlugin, var requester: Sms, requestText: String, var confirmCode: String, val action: SmsAction) {
    private val log = LoggerFactory.getLogger(L.SMS)

    private val date = DateUtil.now()
    private var processed = false

    init {
        plugin.sendSMS(Sms(requester.phoneNumber, requestText))
    }

    fun action(codeReceived: String) {
        if (processed) {
            if (L.isEnabled(L.SMS)) log.debug("Already processed")
            return
        }
        if (confirmCode != codeReceived) {
            processed = true
            if (L.isEnabled(L.SMS)) log.debug("Wrong code")
            plugin.sendSMS(Sms(requester.phoneNumber, R.string.sms_wrongcode))
            return
        }
        if (DateUtil.now() - date < Constants.SMS_CONFIRM_TIMEOUT) {
            processed = true
            if (L.isEnabled(L.SMS)) log.debug("Processing confirmed SMS: " + requester.text)
            action.run()
            return
        }
        if (L.isEnabled(L.SMS)) log.debug("Timed out SMS: " + requester.text)
    }
}