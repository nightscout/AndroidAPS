package app.aaps.core.interfaces.smsCommunicator

import android.telephony.SmsMessage
import kotlin.time.Clock

class Sms {

    var phoneNumber: String
    var text: String
    var date: Long
    var received = false
    var sent = false
    var processed = false
    var ignored = false

    constructor(message: SmsMessage) {
        phoneNumber = message.originatingAddress ?: ""
        text = message.messageBody
        date = message.timestampMillis
        received = true
    }

    constructor(phoneNumber: String, text: String) {
        this.phoneNumber = phoneNumber
        this.text = text
        date = Clock.System.now().toEpochMilliseconds()
        sent = true
    }

    constructor(other: Sms, number: String? = null) {
        phoneNumber = number ?: other.phoneNumber
        text = other.text
        date = other.date
        received = other.received
        sent = other.sent
        processed = other.processed
        ignored = other.ignored
    }

    override fun toString(): String {
        return "SMS from $phoneNumber: $text"
    }
}