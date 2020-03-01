package info.nightscout.androidaps.plugins.general.smsCommunicator

import android.telephony.SmsMessage
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.utils.DateUtil

class Sms {
    var phoneNumber: String
    var text: String
    var date: Long
    var received = false
    var sent = false
    var processed = false
    var ignored = false

    internal constructor(message: SmsMessage) {
        phoneNumber = message.originatingAddress ?: ""
        text = message.messageBody
        date = message.timestampMillis
        received = true
    }

    internal constructor(phoneNumber: String, text: String) {
        this.phoneNumber = phoneNumber
        this.text = text
        date = DateUtil.now()
        sent = true
    }

    internal constructor(phoneNumber: String, textId: Int) {
        this.phoneNumber = phoneNumber
        text = MainApp.gs(textId)
        date = DateUtil.now()
        sent = true
    }

    override fun toString(): String {
        return "SMS from $phoneNumber: $text"
    }
}