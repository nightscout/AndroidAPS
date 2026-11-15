package app.aaps.core.interfaces.smsCommunicator

import kotlin.time.Clock

/**
 * One SMS, in or out.
 *
 * Platform neutral: building one from an `android.telephony.SmsMessage` is an androidMain factory
 * (`Sms.fromSmsMessage`), so nothing here depends on the Android telephony stack.
 */
class Sms {

    var phoneNumber: String
    var text: String
    var date: Long
    var received = false
    var sent = false
    var processed = false
    var ignored = false


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